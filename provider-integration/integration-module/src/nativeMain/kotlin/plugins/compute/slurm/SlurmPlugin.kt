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
import dk.sdu.cloud.ipc.*
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import platform.posix.*

class SlurmPlugin : ComputePlugin {
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()
    lateinit var pluginConfig: SlurmConfig
        private set
    private lateinit var jobCache: JobCache

    override fun configure(config: ConfigSchema.Plugins.Jobs) {
        this.pluginConfig = config as SlurmConfig
    }

    override suspend fun PluginContext.initialize() {
        if (config.serverMode == ServerMode.Server) {
            if (accountMapperOrNull == null) accountMapperOrNull = AccountMapper(this)
            if (productEstimatorOrNull == null) productEstimatorOrNull = ProductEstimator(config)
            jobCache = JobCache(rpcClient)

            initializeIpcServer()
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
        val create = createHandler<SlurmJob, Unit>()

        val retrieve = retrieveHandler<FindByStringId, SlurmJob>()

        // NOTE(Dan): This is not paginated since Slurm does not paginate the results for us
        val browse = browseHandler<SlurmBrowseFlags, List<SlurmJob>>()
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
        val create = createHandler<InteractiveSession, Unit>()
        val retrieve = retrieveHandler<FindByStringId, InteractiveSession>()
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

        val retrieve = retrieveHandler<RetrieveRequest, RetrieveResponse>()
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
    override suspend fun PluginContext.create(resource: Job): FindByStringId? {
        val mountpoint = pluginConfig.mountpoint
        mkdir("${mountpoint}/${resource.id}", "0700".toUInt(8))

        val account = ipcClient.sendRequest(
            SlurmAccountIpc.retrieve,
            SlurmAccountIpc.RetrieveRequest(
                resource.owner,
                resource.specification.product.category,
                pluginConfig.partition
            )
        ).account

        val sbatch = createSbatchFile(this, resource, pluginConfig, account)

        val pathToScript = "${mountpoint}/${resource.id}/job.sbatch"
        NativeFile.open(path = pathToScript, readOnly = false).writeText(sbatch)
        val slurmId = SlurmCommandLine.submitBatchJob(pathToScript)

        ipcClient.sendRequest(SlurmJobsIpc.create, SlurmJob(resource.id, slurmId, pluginConfig.partition))
        return null
    }

    override suspend fun PluginContext.terminate(resource: Job) {
        // NOTE(Dan): Everything else should be handled by the monitoring loop
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(resource.id))
        SlurmCommandLine.cancelJob(slurmJob.partition, slurmJob.slurmId)
    }

    override suspend fun PluginContext.extend(request: JobsProviderExtendRequestItem) {
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override suspend fun PluginContext.suspendJob(request: JobsProviderSuspendRequestItem) {
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override suspend fun ComputePlugin.FollowLogsContext.follow(job: Job) {
        class OutputFile(val rank: Int, val out: NativeFile, val err: NativeFile)

        // TBD: error handling or move below
        val fdList by lazy {
            (0 until job.specification.replicas).map { rank ->
                OutputFile(
                    rank,
                    NativeFile.open(path = "${pluginConfig.mountpoint}/${job.id}/std-${rank}.out", readOnly = true),
                    NativeFile.open(path = "${pluginConfig.mountpoint}/${job.id}/std-${rank}.err", readOnly = true)
                )
            }
        }

        try {
            // NOTE(Dan): Don't open the files until the job looks like it is running
            val currentStatus = getStatus(job.id)

            while (currentStatus.state != JobState.RUNNING && !currentStatus.isFinal) {
                delay(5000)
            }

            // NOTE(Dan): If the job is done, then don't attempt to read the logs
            if (currentStatus.isFinal) return

            while (isActive()) {
                val check: UcloudStateInfo = getStatus(job.id)
                if (check.state == JobState.RUNNING) {

                    fdList.forEach { file ->
                        val line = file.out.readText(autoClose = false)
                        if (line.isNotEmpty()) emitStdout(file.rank, line)

                        val err = file.err.readText(autoClose = false)
                        if (err.isNotEmpty()) emitStderr(file.rank, err)
                    }

                } else break

                sleep(5)
            }
        } finally {
            fdList.forEach { file ->
                file.out.close()
                file.err.close()
            }
        }
    }

    private suspend fun PluginContext.getStatus(id: String): UcloudStateInfo {
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(id))

        val allocationInfo = SlurmCommandLine.browseJobAllocations(listOf(slurmJob.slurmId)).firstOrNull()
            ?: throw RPCException("Unknown Slurm Job Status: $id ($slurmJob)", HttpStatusCode.InternalServerError)

        return SlurmStateToUCloudState.slurmToUCloud.getOrElse(allocationInfo.state) {
            throw RPCException("Unknown Slurm State: $allocationInfo", HttpStatusCode.InternalServerError)
        }
    }

    override suspend fun PluginContext.openInteractiveSession(
        job: JobsProviderOpenInteractiveSessionRequestItem
    ): OpenSession {
        println("OPENSESSIOnHERE $job")

        val sessionId = secureToken(16)
        ipcClient.sendRequest(SlurmSessionIpc.create, InteractiveSession(sessionId, job.rank, job.job.id))

        return OpenSession.Shell(job.job.id, job.rank, sessionId)
    }

    override suspend fun PluginContext.canHandleShellSession(request: ShellRequest.Initialize): Boolean {
        println("CANHANDLESESSION")
        val session: InteractiveSession =
            ipcClient.sendRequest(SlurmSessionIpc.retrieve, FindByStringId(request.sessionIdentifier))
        println("MYSESSIONIS $session")
        return request.sessionIdentifier == session.token
    }

    override suspend fun ComputePlugin.ShellContext.handleShellSession(request: ShellRequest.Initialize) {
        val session: InteractiveSession =
            ipcClient.sendRequest(SlurmSessionIpc.retrieve, FindByStringId(request.sessionIdentifier))
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(session.ucloudId))
        val nodes: Map<Int, String> = SlurmCommandLine.getJobNodeList(slurmJob.slurmId)

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
                }

                else -> {
                    // Nothing to do right now
                }
            }

            val bytesRead: ReadResult = process.stdout!!.read(buffer)
            if (bytesRead.isError) {
                when (bytesRead.getErrorOrThrow()) {
                    EAGAIN -> {
                        delay(15)
                        continue@readloop
                    }
                    else -> break@readloop
                }
            }

            if (!bytesRead.isEof) {
                val decodedString = buffer.decodeToString(0, bytesRead.getOrThrow())
                emitData(decodedString)
            }

            delay(15)
        }

        if (!isActive()) kill(process.pid, SIGINT)
        //TODO: investigate
        // testuser     244       1  0 12:54 ?        00:00:00 [sshd] <defunct>
        // testuser     245       1  0 12:54 ?        00:00:00 [bash] <defunct>
        // testuser     282       1  0 12:54 ?        00:00:00 [bash] <defunct>
    }

    // Server Mode
    // =================================================================================================================
    override suspend fun PluginContext.retrieveClusterUtilization(): JobsProviderUtilizationResponse {
        // Get pending cpu/mem jobs
        var usedCpu = 0
        var usedMem = 0
        var pendingJobs = 0
        var runningJobs = 0

        run {
            // Example output from Slurm:
            // 26|50M|1|PENDING
            // 27|50M|1|PENDING

            val (_, jobs, _) = executeCommandToText(SlurmCommandLine.SQUEUE_EXE) {
                addArg("--format", "%A|%m|%C|%T")
                addArg("--noheader")
                addArg("--noconvert")
                addArg("--states", "running,pending")
                addEnv(SlurmCommandLine.SLURM_CONF_KEY, SlurmCommandLine.SLURM_CONF_VALUE)
            }

            jobs.lineSequence().forEach {
                val line = it.trim().split("|")
                if (line.size < 4) return@forEach

                when (line[3]) {
                    "PENDING" -> {
                        pendingJobs++
                    }

                    "RUNNING" -> {
                        usedCpu += line[2].toInt()
                        usedMem += line[1].replace("M", "").toInt()
                        runningJobs++
                    }
                }
            }
        }

        // Get cluster overall cpu/mem
        var clusterCpu = 0
        var clusterMem = 0

        run {
            // Example output from Slurm:
            // c1|1|1000
            // c2|1|1000

            val (_, nodes, _) = executeCommandToText(SlurmCommandLine.SINFO_EXE) {
                addArg("--format", "%n|%c|%m")
                addArg("--noheader")
                addArg("--noconvert")
                addEnv(SlurmCommandLine.SLURM_CONF_KEY, SlurmCommandLine.SLURM_CONF_VALUE)
            }

            nodes.lineSequence().forEach {
                val line = it.trim().split("|")
                if (line.size < 3) return@forEach
                clusterCpu += line[1].toInt()
                clusterMem += line[2].replace("M", "").toInt()
            }
        }

        return JobsProviderUtilizationResponse(
            CpuAndMemory(clusterCpu.toDouble(), clusterMem.toLong()),
            CpuAndMemory(usedCpu.toDouble(), usedMem.toLong()),
            QueueStatus(runningJobs, pendingJobs)
        )
    }

    override suspend fun PluginContext.runMonitoringLoop() {
        if (config.serverMode != ServerMode.Server) return

        var nextAccountingScan = 0L

        while (currentCoroutineContext().isActive) {
            val now = Time.now()

            try {
                monitorStates()
            } catch (ex: Throwable) {
                log.info("Caught exception while monitoring Slurm states: ${ex.stackTraceToString()}")
            }

            try {
                monitorAccounting(now >= nextAccountingScan)
            } catch (ex: Throwable) {
                log.info("Caught exception while accounting Slurm jobs: ${ex.stackTraceToString()}")
            }

            if (now >= nextAccountingScan) nextAccountingScan = now + 60_000 * 15

            delay(5000)
        }
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


        // Retrieve and process Slurm accounting
        // ------------------------------------------------------------------------------------------------------------
        // In this section we will be performing the following steps:
        // 
        // 1. Retrieve accounting information (Using sacct)
        // 2. Compare with list of known UCloud jobs
        // 3. Register any unknown jobs (if possible, jobs are not required to be for a UCloud project)
        // 4. Establish a mapping between Slurm jobs and UCloud jobs
        val accountingRows = SlurmCommandLine.retrieveAccountingData(lastScan, pluginConfig.partition)
        lastScan = Time.now()

        // TODO(Dan): This loads the entire database every 5 seconds
        val knownJobs = SlurmDatabase.browse(SlurmBrowseFlags())
        val unknownJobs = accountingRows.filter { row ->
            knownJobs.all { it.ucloudId != row.jobId.toString() }
        }

        val jobsToRegister = ArrayList<ProviderRegisteredResource<JobSpecification>>()
        for (job in unknownJobs) {
            if (job.slurmAccount == null) continue
            val potentialAccounts = accountMapper.lookupBySlurm(job.slurmAccount, pluginConfig.partition)
            val (product, replicas, account) = productEstimator.estimateProduct(job, potentialAccounts) ?: continue

            jobsToRegister.add(
                ProviderRegisteredResource(
                    JobSpecification(
                        name = "${job.jobName} (Slurm: ${job.jobId})",
                        application = unknownApplication,
                        product = product,
                        replicas = replicas,

                        parameters = emptyMap(),
                        resources = emptyList(),

                        timeAllocation = SimpleDuration.fromMillis(job.timeAllocationMillis),
                    ),
                    "s-${job.jobId}",
                    account.owner.createdBy,
                    account.owner.project,
                )
            )
        }

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

            val product = ucloudJob.status.resolvedSupport!!.product
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
                    periods,
                )
            )
            accountingCharges.add(row)
        }

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
    }

    private suspend fun PluginContext.monitorStates() {
        val jobs = SlurmDatabase.browse(SlurmBrowseFlags(filterIsActive = true))
        if (jobs.isEmpty()) return

        val acctJobs = SlurmCommandLine.browseJobAllocations(jobs.map { it.slurmId })

        // TODO(Dan): Everything should be more or less ready to do in bulk if needed
        for (job in acctJobs) {
            val dbState = jobs.firstOrNull { it.slurmId == job.jobId }?.lastKnown ?: continue
            val ucloudId = jobs.firstOrNull { it.slurmId == job.jobId }?.ucloudId ?: continue
            val uState = SlurmStateToUCloudState.slurmToUCloud[job.state] ?: continue

            if (uState.providerState != dbState) {
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
    }

    override suspend fun PluginContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<ComputeSupport> {
        return BulkResponse(knownProducts.map { ref ->
            ComputeSupport(
                ref,
                ComputeSupport.Docker(
                    enabled = true,
                    logs = true,
                    timeExtension = true,
                    terminal = true,
                    utilization = true
                )
            )
        })
    }

    @Suppress("VARIABLE_IN_SINGLETON_WITHOUT_THREAD_LOCAL")
    companion object {
        private val log = Log("SlurmPlugin")

        private val unknownApplication = NameAndVersion("unknown", "1")
        private var productEstimatorOrNull: ProductEstimator? = null
        private var accountMapperOrNull: AccountMapper? = null

        private val productEstimator: ProductEstimator
            get() = productEstimatorOrNull!!
        private val accountMapper: AccountMapper
            get() = accountMapperOrNull!!
    }
}
