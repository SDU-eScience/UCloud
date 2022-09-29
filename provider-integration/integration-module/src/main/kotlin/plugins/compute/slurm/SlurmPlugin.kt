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
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.debug.*
import dk.sdu.cloud.ipc.*
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.plugins.storage.posix.PosixCollectionPlugin
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import kotlin.math.max

typealias SlurmConfig = ConfigSchema.Plugins.Jobs.Slurm

class SlurmPlugin : ComputePlugin {
    override val pluginTitle: String = "Slurm"
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()
    lateinit var pluginConfig: SlurmConfig
        private set
    private lateinit var jobCache: JobCache
    private lateinit var cli: SlurmCommandLine
    private var fileCollectionPlugin: PosixCollectionPlugin? = null

    override fun configure(config: ConfigSchema.Plugins.Jobs) {
        this.pluginConfig = config as SlurmConfig
        this.cli = SlurmCommandLine(config.modifySlurmConf)
    }

    override suspend fun PluginContext.initialize() {
        if (config.shouldRunServerCode()) {
            if (accountMapperOrNull == null) accountMapperOrNull = AccountMapper(this)
            if (productEstimatorOrNull == null) productEstimatorOrNull = ProductEstimator(config)
            jobCache = JobCache(rpcClient)

            initializeIpcServer()
        }

        fileCollectionPlugin = config.pluginsOrNull?.fileCollections?.get(pluginName) as? PosixCollectionPlugin
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
    private fun findJobFolder(jobId: String): String? {
        val homeDirectory = homeDirectory()
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
        val jobFolder = findJobFolder(resource.id)

        val account = ipcClient.sendRequest(
            SlurmAccountIpc.retrieve,
            SlurmAccountIpc.RetrieveRequest(
                resource.owner,
                resource.specification.product.category,
                pluginConfig.partition
            )
        ).account

        val sbatch = createSbatchFile(this, resource, pluginConfig, account, jobFolder)

        val pathToScript = if (jobFolder != null) "${jobFolder}/job.sbatch" else "/tmp/${resource.id}.sbatch"
        NativeFile.open(path = pathToScript, readOnly = false, mode = "600".toInt(8)).writeText(sbatch)
        val slurmId = cli.submitBatchJob(pathToScript)

        ipcClient.sendRequest(SlurmJobsIpc.create, SlurmJob(resource.id, slurmId, pluginConfig.partition))

        if (jobFolder != null) {
            fileCollectionPlugin?.let { files ->
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

        return null
    }

    override suspend fun RequestContext.terminate(resource: Job) {
        // NOTE(Dan): Everything else should be handled by the monitoring loop
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(resource.id))
        cli.cancelJob(slurmJob.partition, slurmJob.slurmId)
    }

    override suspend fun RequestContext.extend(request: JobsProviderExtendRequestItem) {
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override suspend fun RequestContext.suspendJob(request: JobsProviderSuspendRequestItem) {
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override suspend fun ComputePlugin.FollowLogsContext.follow(job: Job) {
        val jobFolder = findJobFolder(job.id)

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
        if (job.sessionType != InteractiveSessionType.SHELL) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest)
        }

        return ComputeSession()
    }

    override suspend fun ComputePlugin.ShellContext.handleShellSession(request: ShellRequest.Initialize) {
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(jobId))
        val nodes: Map<Int, String> = cli.getJobNodeList(slurmJob.slurmId)
        val nodeToUse = nodes[jobRank]

        /*
        val session: InteractiveSession =
            ipcClient.sendRequest(SlurmSessionIpc.retrieve, FindByStringId(request.sessionIdentifier))
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(session.ucloudId))

        val process = startProcess(
            args = listOf(
                "/usr/bin/ssh",
                "-tt",
                "-oStrictHostKeyChecking=accept-new",
                "${nodes.get(session.rank)}",
                "([ -x /bin/bash ] && exec /bin/bash) || " +
                    "([ -x /usr/bin/bash ] && exec /usr/bin/bash) || " +
                    "([ -x /bin/zsh ] && exec /bin/zsh) || " +
                    "([ -x /usr/bin/zsh ] && exec /usr/bin/zsh) || " +
                    "([ -x /bin/fish ] && exec /bin/fish) || " +
                    "([ -x /usr/bin/fish ] && exec /usr/bin/fish) || " +
                    "exec /bin/sh"
            ),
            envs = listOf("TERM=xterm-256color"),
            attachStdin = true,
            attachStdout = true,
            attachStderr = true,
            nonBlockingStdout = true,
            nonBlockingStderr = true
        )

        val pStatus = process.retrieveStatus(false)

        val buffer = ByteArray(4096)
        readloop@ while (isActive() && pStatus.isRunning) {
            val userInput = receiveChannel.tryReceive().getOrNull()
            when (userInput) {
                is ShellRequest.Input -> {
                    // Forward input to SSH session
                    //println("USERINPUT: ${ userInput.data }")
                    process.stdin!!.write(userInput.data.encodeToByteArray())
                }

                is ShellRequest.Resize -> {
                    /*
                    // Send resize event to SSH session
                    println("RESIZE EVENT: ${userInput} ")
                    //ps -q 5458 -o tty=
                    val (_, device, _) = executeCommandToText(SlurmCommandLine.PS_EXE) {
                        addArg("-q", "${process.pid}")
                        addArg("-o", "tty=")
                    }

                    println("DEVICE IS ${device.trim()} and ${userInput.rows} and ${userInput.cols} ")

                    //stty rows 50 cols 50 --file /dev/pts/0
                    val (_, _, _) = executeCommandToText(SlurmCommandLine.STTY_EXE) {
                        addArg("rows", "700")
                        addArg("cols", "700")
                        addArg("--file", "/dev/${device.trim()}")
                    }
                     */
                }

                else -> {
                    // Nothing to do right now
                }
            }

            val bytesRead: ReadResult = process.stdout!!.read(buffer)
            if (bytesRead.isError) {
                break@readloop
            }

            if (!bytesRead.isEof) {
                val decodedString = buffer.decodeToString(0, bytesRead.getOrThrow())
                emitData(decodedString)
            }

            delay(15)
        }

        if (!isActive()) process.jvm.destroy()
        //TODO: investigate
        // testuser     244       1  0 12:54 ?        00:00:00 [sshd] <defunct>
        // testuser     245       1  0 12:54 ?        00:00:00 [bash] <defunct>
        // testuser     282       1  0 12:54 ?        00:00:00 [bash] <defunct>
         */
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
            if (Time.now() >= nextAccountingScan) nextAccountingScan = Time.now() + 60_000 * 15
        }
    }

    // NOTE(Dan): This cannot be inlined in the loop due to a limitation in GraalVM (AOT native images only)
    private suspend fun PluginContext.loop(accountingScan: Boolean) {
        debugSystem.enterContext("Slurm monitoring") {
            try {
                monitorStates()
            } catch (ex: Throwable) {
                debugSystem.logThrowable("Slurm monitoring error", ex, MessageImportance.THIS_IS_WRONG)
            }
        }

        debugSystem.enterContext("Slurm accounting") {
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
        //    - It is important that we run this step frequently to ensure that jobs submitted outside of UCloud are
        //      discovered quickly. This allows the end-user to track the job via the UCloud interface.
        //
        // 2. Send accounting information to UCloud
        //    - Combines the output of phase 1 with locally stored information to create charge requests
        //    - Register these charges with the accounting system of UCloud

        var registeredJobs = 0
        var chargedJobs = 0


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

        debugSystem.logD(
            "Registered $registeredJobs slurm jobs",
            Unit.serializer(),
            Unit,
            if (registeredJobs == 0) MessageImportance.IMPLEMENTATION_DETAIL
            else MessageImportance.THIS_IS_NORMAL
        )

        // Send accounting information to UCloud
        // ------------------------------------------------------------------------------------------------------------
        // In this section we will be performing the following steps:
        //
        // 1. Combine the two lists from the first phase to create batches of charge requests
        // 2. For each batch, optionally process this through an extension which can modify the list
        // 3. Send the batch to UCloud
        if (!emitAccountingInfo) return

        val activeJobs = SlurmDatabase.browse(SlurmBrowseFlags(filterIsActive = true))
        val charges = ArrayList<ResourceChargeCredits>()
        val accountingCharges = ArrayList<SlurmCommandLine.SlurmAccountingRow>()
        for (job in activeJobs) {
            val ucloudJob = jobCache.findJob(job.ucloudId) ?: continue
            val row = accountingRows.find { it.jobId.toString() == job.slurmId } ?: continue
            val timeSinceLastUpdate = row.timeElappsedMs - job.elapsed
            if (timeSinceLastUpdate <= 0) continue

            val product = ucloudJob.status.resolvedProduct!!
            val periods = when (product.unitOfPrice) {
                ProductPriceUnit.CREDITS_PER_UNIT,
                ProductPriceUnit.PER_UNIT,
                ProductPriceUnit.CREDITS_PER_MINUTE,
                ProductPriceUnit.UNITS_PER_MINUTE -> {
                    timeSinceLastUpdate / (1000L * 60)
                }

                ProductPriceUnit.CREDITS_PER_HOUR,
                ProductPriceUnit.UNITS_PER_HOUR -> {
                    timeSinceLastUpdate / (1000L * 60 * 60)
                }

                ProductPriceUnit.CREDITS_PER_DAY,
                ProductPriceUnit.UNITS_PER_DAY -> {
                    timeSinceLastUpdate / (1000L * 60 * 60 * 24)
                }
            }

            val units = ((product.cpu ?: 1) * ucloudJob.specification.replicas).toLong()

            charges.add(
                ResourceChargeCredits(
                    job.ucloudId,
                    job.ucloudId + "_" + job.elapsed,
                    units,
                    max(1, periods),
                )
            )
            accountingCharges.add(row)
        }

        chargedJobs = charges.size

        val batchSize = 100
        for ((index, batch) in charges.chunked(batchSize).withIndex()) {
            val offset = batchSize * index
            val success = JobsControl.chargeCredits.call(
                BulkRequest(batch),
                rpcClient
            ) is IngoingCallResponse.Ok<*, *>

            if (!success) continue

            dbConnection.withSession { session ->
                SlurmDatabase.updateElapsedByUCloudId(
                    batch.map { it.id },
                    List(batch.size) { idx ->
                        accountingCharges[offset + idx].timeElappsedMs
                    },
                    session
                )

                val inactiveJobs = ArrayList<String>()
                for (idx in batch.indices) {
                    val row = accountingCharges[offset + idx]
                    if (row.state.isFinal) inactiveJobs.add(row.jobId.toString())
                }

                if (inactiveJobs.isNotEmpty()) {
                    SlurmDatabase.markAsInactive(inactiveJobs, session)
                }
            }
        }

        debugSystem.logD(
            "Charged $chargedJobs slurm jobs",
            Unit.serializer(),
            Unit,
            if (chargedJobs == 0) MessageImportance.IMPLEMENTATION_DETAIL
            else MessageImportance.THIS_IS_NORMAL
        )
    }

    private suspend fun PluginContext.monitorStates() {
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
                updatesSent++
                val updateResponse = JobsControl.update.call(
                    bulkRequestOf(
                        ResourceUpdateAndId(
                            ucloudId,
                            JobUpdate(uState.state, status = uState.message)
                        )
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

        debugSystem.logD(
            "Updated state of $updatesSent slurm jobs",
            Unit.serializer(),
            Unit,
            if (updatesSent == 0) MessageImportance.IMPLEMENTATION_DETAIL
            else MessageImportance.THIS_IS_NORMAL,
        )
    }

    override suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<ComputeSupport> {
        return BulkResponse(knownProducts.map { ref ->
            ComputeSupport(
                ref,
                docker = ComputeSupport.Docker(
                    enabled = true,
                    logs = true,
                    timeExtension = false,
                    terminal = true,
                    utilization = false,
                ),
                native = ComputeSupport.Native(
                    enabled = true,
                    logs = true,
                    timeExtension = false,
                    terminal = true,
                    utilization = false,
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
    }
}
