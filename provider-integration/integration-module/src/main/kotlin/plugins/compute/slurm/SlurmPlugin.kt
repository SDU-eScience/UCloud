package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.ComputeSessionIpc
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.controllers.UserMapping
import dk.sdu.cloud.debug.*
import dk.sdu.cloud.ipc.*
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.plugins.compute.udocker.UDocker
import dk.sdu.cloud.plugins.storage.posix.PosixCollectionIpc
import dk.sdu.cloud.plugins.storage.posix.PosixCollectionPlugin
import dk.sdu.cloud.plugins.storage.ucloud.DefaultDirectBufferPool
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.*
import io.ktor.util.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import libc.clib
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList
import kotlin.math.max
import dk.sdu.cloud.config.ConfigSchema.Plugins.Jobs.Slurm as SlurmConfig

class SlurmPlugin : ComputePlugin {
    override val pluginTitle: String = "Slurm"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<ProductV2> = emptyList()
    lateinit var pluginConfig: SlurmConfig
        private set
    private lateinit var jobCache: JobCache
    private lateinit var cli: SlurmCommandLine
    private var lastAccountingCharge = 0L
    private var fileCollectionPlugin: PosixCollectionPlugin? = null
    var udocker: UDocker? = null
        private set
    private val didInitKeys = AtomicBoolean(false)
    private lateinit var ctx: PluginContext
    var constraintMatchers: List<VerifiedConfig.Plugins.ProductMatcher> = emptyList()
        private set

    override fun configure(config: ConfigSchema.Plugins.Jobs) {
        this.pluginConfig = config as SlurmConfig
        this.cli = SlurmCommandLine(config.modifySlurmConf)
    }

    override suspend fun PluginContext.initialize() {
        ctx = this

        val matchers = ArrayList<VerifiedConfig.Plugins.ProductMatcher>()
        for (constraint in pluginConfig.constraints) {
            val matcher = handleVerificationResultStrict(
                VerifiedConfig.Plugins.ProductMatcher.parse(constraint.matches)
            )
            matchers.add(matcher)
        }
        constraintMatchers = matchers

        if (config.shouldRunServerCode()) {
            if (accountMapperOrNull == null) accountMapperOrNull = AccountMapper(this)
            if (productEstimatorOrNull == null) productEstimatorOrNull = ProductEstimator(config)
            jobCache = JobCache(rpcClient)

            initializeIpcServer()
        }

        fileCollectionPlugin = config.pluginsOrNull?.fileCollections?.get(pluginName) as? PosixCollectionPlugin
        if (pluginConfig.udocker.enabled) {
            udocker = UDocker(this).also { it.init() }
        }
    }

    // IPC
    // =================================================================================================================
    private fun PluginContext.initializeIpcServer() {
        initializeJobsIpc()
        initializeSessionIpc()
        initializeAccountIpc()
    }

    private object SlurmJobsIpc : IpcContainer("slurm.jobs") {
        val create = createHandler(SlurmJob.serializer(), Unit.serializer())

        val retrieve = retrieveHandler(FindByStringId.serializer(), SlurmJob.serializer())

        // NOTE(Dan): This is not paginated since Slurm does not paginate the results for us
        val browse = browseHandler(SlurmBrowseFlags.serializer(), ListSerializer(SlurmJob.serializer()))
    }

    private fun PluginContext.initializeJobsIpc() {
        SlurmJobsIpc.create.handler { user, request ->
            SlurmDatabase.registerJob(request)
        }.register(ipcServer)

        SlurmJobsIpc.retrieve.handler { user, request ->
            SlurmDatabase.retrieveByUCloudId(request.id) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }.register(ipcServer)

        SlurmJobsIpc.browse.handler { user, request ->
            SlurmDatabase.browse(request)
        }.register(ipcServer)
    }

    private object SlurmSessionIpc : IpcContainer("slurm.sessions") {
        val create = createHandler(InteractiveSession.serializer(), Unit.serializer())
        val retrieve = retrieveHandler(FindByStringId.serializer(), InteractiveSession.serializer())
    }

    private fun PluginContext.initializeSessionIpc() {
        SlurmSessionIpc.create.handler { user, request ->
            SlurmDatabase.registerSession(request)
        }.register(ipcServer)

        SlurmSessionIpc.retrieve.handler { user, request ->
            SlurmDatabase.retrieveSession(request) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }.register(ipcServer)
    }

    private object SlurmAccountIpc : IpcContainer("slurm.account") {
        @Serializable
        data class RetrieveRequest(
            val owner: ResourceOwner,
            val productCategory: String,
            val partition: String
        )

        @Serializable
        data class RetrieveResponse(
            val account: String?
        )

        val retrieve = retrieveHandler(RetrieveRequest.serializer(), RetrieveResponse.serializer())
    }

    private fun PluginContext.initializeAccountIpc() {
        SlurmAccountIpc.retrieve.suspendingHandler { user, request ->
            SlurmAccountIpc.RetrieveResponse(
                accountMapper.lookupByUCloud(request.owner, request.productCategory, request.partition)?.account
            )
        }.register(ipcServer)
    }

    // User Mode
    // =================================================================================================================
    private var baseJobDirectory: String? = null
    private val baseJobDirectoryMutex = Mutex()
    private suspend fun findJobFolder(job: Job): String? {
        val jobId = job.id
        val homeDirectory: String = if (baseJobDirectory != null) {
            baseJobDirectory!!
        } else {
            baseJobDirectoryMutex.withLock {
                if (baseJobDirectory != null) {
                    baseJobDirectory!!
                } else {
                    val baseDir = ctx.ipcClient.sendRequest(
                        PosixCollectionIpc.retrieveCollections,
                        job.owner
                    ).items.singleOrNull()?.id ?: homeDirectory()

                    baseJobDirectory = baseDir
                    baseDir
                }
            }
        }

        if (!fileExists(homeDirectory)) return null
        val jobsDir = homeDirectory.removeSuffix("/") + "/UCloud Jobs"
        if (!fileExists(jobsDir)) {
            if (!File(jobsDir).mkdirs()) {
                return null
            }
        }

        if (!fileIsDirectory(jobsDir)) return null
        val jobDirectory = "${jobsDir}/${jobId}"
        return if (fileIsDirectory(jobDirectory)) {
            return jobDirectory
        } else if (!File(jobDirectory).mkdirs()) {
            null
        } else {
            jobDirectory
        }
    }

    override suspend fun RequestContext.create(resource: Job): FindByStringId? {
        val jobFolder = findJobFolder(resource)

        val account = ipcClient.sendRequest(
            SlurmAccountIpc.retrieve,
            SlurmAccountIpc.RetrieveRequest(
                resource.owner,
                resource.specification.product.category,
                pluginConfig.partition
            )
        ).account

        val sbatch = createSbatchFile(this, resource, this@SlurmPlugin, account, jobFolder)

        val pathToScript = if (jobFolder != null) "${jobFolder}/job.sbatch" else "/tmp/${resource.id}.sbatch"
        NativeFile.open(path = pathToScript, readOnly = false, mode = "600".toInt(8)).writeText(sbatch)
        val slurmId = cli.submitBatchJob(pathToScript)

        ipcClient.sendRequest(SlurmJobsIpc.create, SlurmJob(resource.id, slurmId, pluginConfig.partition))

        if (jobFolder != null) {
            fileCollectionPlugin?.let { files ->
                runCatching {
                    val path = files.pathConverter.internalToUCloud(InternalFile(jobFolder)).path
                    JobsControl.update.call(
                        bulkRequestOf(
                            ResourceUpdateAndId(
                                resource.id,
                                JobUpdate(outputFolder = path)
                            )
                        ),
                        rpcClient,
                    )
                }
            }
        }

        return null
    }

    override suspend fun RequestContext.terminate(resource: Job) {
        // NOTE(Dan): Everything else should be handled by the monitoring loop
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(resource.id))
        if (!cli.cancelJob(slurmJob.partition, slurmJob.slurmId)) {
            JobsControl.update.call(
                bulkRequestOf(
                    ResourceUpdateAndId(
                        resource.id,
                        JobUpdate(state = JobState.SUCCESS)
                    )
                ),
                rpcClient
            ).orRethrowAs {
                throw RPCException(
                    "Something went wrong while attempting to cancel the job. Please try again.",
                    HttpStatusCode.BadGateway
                )
            }
        }
    }

    override suspend fun RequestContext.extend(request: JobsProviderExtendRequestItem) {
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override suspend fun RequestContext.suspendJob(request: JobsProviderSuspendRequestItem) {
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override suspend fun ComputePlugin.FollowLogsContext.follow(job: Job) {
        val jobFolder = findJobFolder(job)

        class OutputFile(val rank: Int, val out: NativeFile?, val err: NativeFile?)

        val openFiles = ArrayList<OutputFile>()

        try {
            // NOTE(Dan): Don't open the files until the job looks like it is running
            val currentStatus = getStatus(job.id)

            while (currentStatus.state != JobState.RUNNING && !currentStatus.isFinal) {
                delay(5000)
            }

            // NOTE(Dan): If the job is done, then don't attempt to read the logs
            if (currentStatus.isFinal) return

            for (rank in 0 until job.specification.replicas) {
                var stdout = runCatching {
                    NativeFile.open(path = "${jobFolder}/std-${rank}.out", readOnly = true)
                }.getOrNull()

                var stderr = runCatching {
                    NativeFile.open(path = "${jobFolder}/std-${rank}.err", readOnly = true)
                }.getOrNull()

                if (stdout == null && stderr == null) {
                    runCatching {
                        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(job.id))
                        val logFileLocation = cli.readLogFileLocation(slurmJob.slurmId)

                        if (logFileLocation.stdout != null) {
                            stdout = runCatching { NativeFile.open(logFileLocation.stdout, readOnly = true) }
                                .getOrNull()
                        }

                        if (logFileLocation.stderr != null) {
                            stderr = runCatching { NativeFile.open(logFileLocation.stderr, readOnly = true) }
                                .getOrNull()
                        }
                    }
                }

                if (stdout == null && stderr == null) {
                    emitStdout(
                        rank,
                        "Unable to read logs. If the job was submitted outside of UCloud, then we might not be " +
                                "able to read the logs automatically."
                    )
                }

                openFiles.add(OutputFile(rank, stdout, stderr))
            }

            while (isActive()) {
                val check = getStatus(job.id)
                if (check.state != JobState.RUNNING) break

                openFiles.forEach { file ->
                    val line = file.out?.readText(charLimit = 1024, autoClose = false, allowLongMessage = true) ?: ""
                    if (line.isNotEmpty()) emitStdout(file.rank, line)

                    val err = file.err?.readText(charLimit = 1024, autoClose = false, allowLongMessage = true) ?: ""
                    if (err.isNotEmpty()) emitStderr(file.rank, err)
                }

                delay(5000)
            }
        } finally {
            openFiles.forEach { file ->
                file.out?.close()
                file.err?.close()
            }
        }
    }

    private suspend fun PluginContext.getStatus(id: String): UcloudStateInfo {
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(id))

        val allocationInfo = cli.browseJobAllocations(listOf(slurmJob.slurmId)).firstOrNull()
            ?: throw RPCException("Unknown Slurm Job Status: $id ($slurmJob)", HttpStatusCode.InternalServerError)

        return SlurmStateToUCloudState.slurmToUCloud.getOrElse(allocationInfo.state) {
            throw RPCException("Unknown Slurm State: $allocationInfo", HttpStatusCode.InternalServerError)
        }
    }

    override suspend fun RequestContext.openInteractiveSession(
        job: JobsProviderOpenInteractiveSessionRequestItem
    ): ComputeSession {
        return when (job.sessionType) {
            InteractiveSessionType.WEB -> {
                val webConfig = pluginConfig.web
                if (webConfig !is ConfigSchema.Plugins.Jobs.Slurm.Web.Simple) {
                    throw RPCException("Unsupported operation", HttpStatusCode.BadRequest)
                }

                val jobId = job.job.id
                val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(jobId))
                val nodes = cli.getJobNodeList(slurmJob.slurmId)
                val nodeToUse = nodes[job.rank]
                    ?: throw RPCException("Unable to connect to job", HttpStatusCode.BadGateway)
                val jobFolder = findJobFolder(job.job)
                    ?: throw RPCException("Unable to connect to job", HttpStatusCode.BadGateway)
                val port = runCatching { File(jobFolder, ALLOCATED_PORT_FILE).readText().trim().toInt() }.getOrNull()
                    ?: throw RPCException("Unable to connect to job - Try again later", HttpStatusCode.BadGateway)

                ComputeSession(
                    target = ComputeSessionIpc.SessionTarget(
                        webConfig.domainPrefix + jobId + webConfig.domainSuffix,
                        nodeToUse,
                        port,
                        webSessionIsPublic = false,
                        useDnsForAddressLookup = true
                    )
                )
            }

            InteractiveSessionType.SHELL -> {
                shellTracer { "Starting to open Slurm shell" }
                val terminal = pluginConfig.terminal
                shellTracer { "$terminal" }
                val generateSshKeys = (terminal as? SlurmConfig.Terminal.Ssh)?.generateSshKeys
                if (terminal.enabled && generateSshKeys == true && didInitKeys.compareAndSet(false, true)) {
                    shellTracer { "We need to generate an SSH key" }
                    val publicKeyFile = File("${homeDirectory()}/.ssh/$sshId.pub")

                    if (!publicKeyFile.exists()) {
                        executeCommandToText("/usr/bin/ssh-keygen") {
                            addArg("-t", "rsa")
                            addArg("-q")
                            addArg("-f", "${homeDirectory()}/.ssh/$sshId")
                            addArg("-N", "")
                        }

                        val publicKey = publicKeyFile.readText()

                        val authorizedKeysFile = "${homeDirectory()}/.ssh/authorized_keys"
                        File(authorizedKeysFile).appendText(publicKey + "\n")
                        clib.chmod(authorizedKeysFile, "600".toInt(8))
                    }
                }

                shellTracer { "Success! We are ready to send a session." }
                ComputeSession()
            }

            else -> throw RPCException("Not supported", HttpStatusCode.BadRequest)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun ComputePlugin.ShellContext.handleShellSession(request: ShellRequest.Initialize) {
        val shellIsActive = isActive
        shellTracer { "Looking for slurm job $jobId" }
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(jobId))
        shellTracer { "Got a job $slurmJob" }
        val nodes: Map<Int, String> = cli.getJobNodeList(slurmJob.slurmId)
        shellTracer { "Found nodes $nodes" }
        val nodeToUse = nodes[jobRank] ?: throw RPCException("Could not locate job node", HttpStatusCode.BadGateway)
        shellTracer { "Found the node to use $nodeToUse" }

        val masterFd = clib.createAndForkPty(
            command = buildList {
                when (pluginConfig.terminal) {
                    is SlurmConfig.Terminal.Ssh -> {
                        shellTracer { "Using SSH to connect to $nodeToUse" }
                        add("/usr/bin/ssh")
                        add("-tt")
                        add("-oStrictHostKeyChecking=accept-new")

                        if ((pluginConfig.terminal as? SlurmConfig.Terminal.Ssh)?.generateSshKeys == true) {
                            add("-i")
                            add("${homeDirectory()}/.ssh/${sshId}")
                        }

                        add(nodeToUse)
                        add("bash --login")
                    }

                    is SlurmConfig.Terminal.Slurm -> {
                        shellTracer { "Using srun to connect to $nodeToUse" }
                        add(SlurmCommandLine.SRUN_EXE)
                        add("--overlap")
                        add("--pty")
                        add("--jobid=${slurmJob.slurmId}")
                        add("-w")
                        add(nodeToUse)
                        add("bash")
                        add("--login")
                    }
                }
            }.also { shellTracer { "Command is $it" } }.toTypedArray(),
            env = arrayOf(
                "TERM", "xterm"
            )
        )
        shellTracer { "master fd is $masterFd" }
        val processInput = LinuxOutputStream(LinuxFileHandle.createOrThrow(masterFd, { error("Bad fd") }))
        val processOutput = LinuxInputStream(LinuxFileHandle.createOrThrow(masterFd, { error("Bad fd") }))

        val userInputToSsh = ProcessingScope.launch {
            shellTracer { "userInputToSsh init" }
            while (shellIsActive() && isActive && !receiveChannel.isClosedForReceive) {
                shellTracer { "userInputToSsh spin" }
                select {
                    receiveChannel.onReceiveCatching {
                        shellTracer { "Got input?" }
                        val userInput = it.getOrNull() ?: return@onReceiveCatching Unit
                        shellTracer { "input = $userInput" }

                        when (userInput) {
                            is ShellRequest.Input -> {
                                processInput.writeString(userInput.data)
                            }

                            is ShellRequest.Resize -> {
                                clib.resizePty(masterFd, userInput.cols, userInput.rows)
                            }

                            else -> {
                                // Nothing to do right now
                            }
                        }

                        Unit
                    }

                    onTimeout(500) {
                        // Check if shellIsActive() and isActive
                    }
                }
            }
        }

        val sshOutputToUser = ProcessingScope.launch {
            val readBuffer = ByteBuffer.allocateDirect(256)

            shellTracer { "sshOutputToUser ready" }
            while (shellIsActive() && isActive) {
                shellTracer { "sshOutputToUser spin" }
                val bytesRead = processOutput.read(readBuffer)
                shellTracer { "sshOutputToUser read $bytesRead" }

                if (bytesRead <= 0) break
                readBuffer.flip()
                val decodedString = readBuffer.decodeString()
                readBuffer.clear()
                emitData(decodedString)
            }
        }

        while (currentCoroutineContext().isActive) {
            val shouldBreak = select {
                sshOutputToUser.onJoin {
                    shellTracer { "Join sshOutputToUser" }
                    true
                }

                userInputToSsh.onJoin {
                    shellTracer { "Join sshOutputToUser" }
                    true
                }

                onTimeout(500) {
                    val shouldBreak = !shellIsActive()
                    if (shouldBreak) {
                        shellTracer { "Breaking because shell is no longer active" }
                    }
                    shouldBreak
                }
            }

            if (shouldBreak) break
        }

        runCatching { sshOutputToUser.cancel() }
        runCatching { userInputToSsh.cancel() }
        runCatching { processInput.close() }
        runCatching { processOutput.close() }
    }

    // Server Mode
    // =================================================================================================================
    override suspend fun RequestContext.retrieveClusterUtilization(categoryId: String): JobsProviderUtilizationResponse {
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override suspend fun PluginContext.runMonitoringLoopInServerMode() {
        var nextAccountingScan = 0L

        while (currentCoroutineContext().isActive) {
            loop(Time.now() >= nextAccountingScan)
            if (lastAccountingCharge >= nextAccountingScan) nextAccountingScan = Time.now() + 60_000 * 15
        }
    }

    // NOTE(Dan): This cannot be inlined in the loop due to a limitation in GraalVM (AOT native images only)
    private suspend fun PluginContext.loop(accountingScan: Boolean) {
        debugSystem.useContext(DebugContextType.BACKGROUND_TASK, "Slurm monitoring") {
            try {
                monitorStates()
            } catch (ex: Throwable) {
                debugSystem.logThrowable("Slurm monitoring error", ex, MessageImportance.THIS_IS_WRONG)
            }
        }

        debugSystem.useContext(DebugContextType.BACKGROUND_TASK, "Slurm accounting") {
            try {
                monitorAccounting(accountingScan)
            } catch (ex: Throwable) {
                debugSystem.logThrowable("Slurm monitoring error", ex, MessageImportance.THIS_IS_WRONG)
            }
        }

        delay(5000)
    }

    private var lastScan: Long = 0L
    private suspend fun PluginContext.monitorAccounting(emitAccountingInfo: Boolean) {
        // The job of this function is to monitor everything related to accounting. We run through two phases, each 
        // running at different frequencies:
        //
        // 1. Retrieve and process Slurm accounting
        //    - Invokes sacct to determine which jobs are in the system
        //    - Importantly, this job will efficiently determine which jobs are _not_ currently known by UCloud and
        //      register them into UCloud.
        //    - It is important that we run this step frequently to ensure that jobs submitted outside UCloud are
        //      discovered quickly. This allows the end-user to track the job via the UCloud interface.
        //
        // 2. Send accounting information to UCloud

        var registeredJobs = 0

        // Retrieve and process Slurm accounting
        // ------------------------------------------------------------------------------------------------------------
        // In this section we will be performing the following steps:
        // 
        // 1. Retrieve accounting information (Using sacct)
        // 2. Compare with list of known UCloud jobs
        // 3. Register any unknown jobs (if possible, jobs are not required to be for a UCloud project)
        // 4. Establish a mapping between Slurm jobs and UCloud jobs
        val accountingRows = cli.retrieveAccountingData(lastScan, pluginConfig.partition)
        lastScan = Time.now()

        // TODO(Dan): This loads the entire database every 5 seconds
        val knownJobs = SlurmDatabase.browse(SlurmBrowseFlags())
        val unknownJobs = accountingRows.filter { row ->
            knownJobs.all { it.slurmId != row.jobId.toString() }
        }

        val jobsToRegister = ArrayList<ProviderRegisteredResource<JobSpecification>>()
        for (job in unknownJobs) {
            if (job.slurmAccount == null) {
                continue
            }
            val potentialAccounts = accountMapper.lookupBySlurm(job.slurmAccount, pluginConfig.partition)
            val estimateProduct = productEstimator.estimateProduct(job, potentialAccounts)
            val (product, replicas, account) = estimateProduct ?: continue

            val desiredName = "${job.jobName} (SlurmID: ${job.jobId})"
            val safeName = desiredName.replace(jobNameUnsafeRegex, "")
            jobsToRegister.add(
                ProviderRegisteredResource(
                    JobSpecification(
                        name = safeName,
                        application = unknownApplication,
                        product = product,
                        replicas = replicas,

                        parameters = emptyMap(),
                        resources = emptyList(),

                        timeAllocation = SimpleDuration.fromMillis(job.timeAllocationMillis),
                    ),
                    "s-${job.jobId}", // TODO(Dan): Slurm job id wrap-around makes these not unique
                    account.owner.createdBy,
                    account.owner.project,
                )
            )
        }

        registeredJobs = jobsToRegister.size

        for (batch in jobsToRegister.chunked(100)) {
            if (batch.isEmpty()) continue
            val ids = JobsControl.register.call(
                BulkRequest(batch),
                rpcClient,
            ).orNull()?.responses ?: continue

            dbConnection.withSession { session ->
                for (i in batch.indices) {
                    val jobSpecification = batch[i]
                    val ucloudId = ids[i].id
                    val slurmId = jobSpecification.providerGeneratedId?.removePrefix("s-") ?: continue

                    SlurmDatabase.registerJob(
                        SlurmJob(
                            ucloudId,
                            slurmId,
                            pluginConfig.partition
                        ),
                        session
                    )
                }
            }
        }

        debugSystem.normal("Registered $registeredJobs slurm jobs")

        // Send accounting information to UCloud
        // ------------------------------------------------------------------------------------------------------------
        if (!emitAccountingInfo) return

        val ext = pluginConfig.extensions.reportComputeUsage ?: return
        val responses = reportComputeUsageExtension.invoke(this, ext, Unit)
        for (resp in responses) {
            when (resp) {
                is ReportComputeUsageResponse.Slurm -> {
                    val workspace = accountMapper.lookupBySlurm(resp.account, resp.partition).firstOrNull()
                    if (workspace != null) {
                        val owner = walletOwnerFromOwnerString(workspace.owner.toSimpleString())
                        reportBalance(owner, ProductCategoryIdV2(workspace.productCategory, providerId), resp.usage)
                    }
                }

                is ReportComputeUsageResponse.UCloud -> {
                    val owner = walletOwnerFromOwnerString(resp.workspace)
                    reportBalance(owner, ProductCategoryIdV2(resp.category, providerId), resp.usage)
                }
            }
        }
    }

    private val uidToUsernameCache = SimpleCache<Int, String>(
        maxAge = SimpleCache.DONT_EXPIRE,
        lookup = { uid ->
            val process = executeCommandToText("/bin/getent") {
                addArg("passwd")
                addArg("$uid")
            }

            if (process.statusCode != 0) {
                null
            } else {
                process.stdout.split(":").getOrNull(0)
            }
        }
    )

    private suspend fun PluginContext.monitorStates() {
        require(config.shouldRunServerCode())

        val jobs = SlurmDatabase.browse(SlurmBrowseFlags(filterIsActive = true))
        if (jobs.isEmpty()) return

        val acctJobs = cli.browseJobAllocations(jobs.map { it.slurmId })

        var updatesSent = 0
        // TODO(Dan): Everything should be more or less ready to do in bulk if needed
        for (job in acctJobs) {
            val dbState = jobs.firstOrNull { it.slurmId == job.jobId }?.lastKnown ?: continue
            val ucloudId = jobs.firstOrNull { it.slurmId == job.jobId }?.ucloudId ?: continue
            val uState = SlurmStateToUCloudState.slurmToUCloud[job.state] ?: continue

            if (uState.state.name != dbState) {
                val sshConfig = pluginConfig.ssh

                updatesSent++
                val updateResponse = JobsControl.update.call(
                    BulkRequest(
                        buildList {
                            add(
                                ResourceUpdateAndId(
                                    ucloudId,
                                    JobUpdate(uState.state, status = uState.message)
                                )
                            )

                            if (uState.state == JobState.RUNNING && sshConfig != null) {
                                val createdBy = runCatching { jobCache.findJob(ucloudId)?.owner?.createdBy }.getOrNull()
                                if (createdBy != null) {
                                    val localUid = UserMapping.ucloudIdToLocalId(createdBy)
                                    if (localUid != null) {
                                        val localUsername = uidToUsernameCache.get(localUid)
                                        if (localUsername != null) {
                                            add(
                                                ResourceUpdateAndId(
                                                    ucloudId,
                                                    JobUpdate(
                                                        status = buildString {
                                                            append("SSH: ")
                                                            append("Connected! ")
                                                            append("Available at: ")
                                                            append("ssh ")
                                                            append(localUsername)
                                                            append('@')
                                                            append(sshConfig.publicHost)
                                                            if (sshConfig.port != null) {
                                                                append(" -p ")
                                                                append(sshConfig.port)
                                                            }
                                                        }
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ),
                    rpcClient,
                )

                if (updateResponse is IngoingCallResponse.Ok<*, *>) {
                    SlurmDatabase.updateState(listOf(job.jobId), listOf(uState.state))
                } else {
                    log.warn("Caught an exception while updating job status: $updateResponse")
                }
            }
        }

        debugSystem.normal("Updated state of $updatesSent slurm jobs")
    }

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<ComputeSupport> {
        return BulkResponse(knownProducts.map { ref ->
            ComputeSupport(
                ref,
                docker = ComputeSupport.Docker(
                    enabled = pluginConfig.udocker.enabled,
                    logs = true,
                    timeExtension = false,
                    terminal = pluginConfig.terminal.enabled,
                    utilization = false,
                ),
                native = ComputeSupport.Native(
                    enabled = true,
                    logs = true,
                    timeExtension = false,
                    terminal = pluginConfig.terminal.enabled,
                    utilization = false,
                    web = pluginConfig.web !is ConfigSchema.Plugins.Jobs.Slurm.Web.None,
                ),
            )
        })
    }

    companion object {
        private val log = Logger("SlurmPlugin")

        private val unknownApplication = NameAndVersion("unknown", "unknown")
        private var productEstimatorOrNull: ProductEstimator? = null
        private var accountMapperOrNull: AccountMapper? = null

        private val productEstimator: ProductEstimator
            get() = productEstimatorOrNull!!
        private val accountMapper: AccountMapper
            get() = accountMapperOrNull!!

        private val jobNameUnsafeRegex = Regex("""[^\w ():_-]""")

        const val ALLOCATED_PORT_FILE = "allocated-port.txt"
        private const val sshId = "id_ucloud_im"

        val reportComputeUsageExtension = extension(Unit.serializer(), ListSerializer(ReportComputeUsageResponse.serializer()))
    }
}

@Serializable
sealed class ReportComputeUsageResponse {
    @Serializable
    @SerialName("Slurm")
    data class Slurm(
        val account: String,
        val partition: String,
        val usage: Long,
    ) : ReportComputeUsageResponse()

    @Serializable
    @SerialName("UCloud")
    data class UCloud(
        val workspace: String,
        val category: String,
        val usage: Long,
    ) : ReportComputeUsageResponse()
}
