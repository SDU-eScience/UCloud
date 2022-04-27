package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import platform.posix.ceil
import platform.posix.mkdir
import platform.posix.sleep
import platform.posix.kill
import platform.posix.usleep
import kotlin.native.concurrent.AtomicReference

import dk.sdu.cloud.utils.secureToken
import platform.posix.*
// import kotlinx.coroutines.runBlocking
// import kotlinx.coroutines.async
// import kotlinx.coroutines.awaitAll


@Serializable
data class SlurmConfiguration(
    val partition: String,
    val mountpoint: String,
    val useFakeMemoryAllocations: Boolean = false
)

class SlurmPlugin : ComputePlugin {
    private val pluginConfig = AtomicReference<ProductBasedConfiguration?>(null)

    private fun config(product: ProductReference): SlurmConfiguration {
        val globalConfig = pluginConfig.value ?: error("Configuration not yet ready!")
        val ref = ProductReferenceWithoutProvider(product.id, product.category)
        val relevantConfig = globalConfig.plugins.find { config -> config.activeFor.any { it.matches(ref) } }
            ?.configuration ?: error("No configuration found for product: $ref")

        return try {
            defaultMapper.decodeFromJsonElement(relevantConfig)
        } catch (ex: Throwable) {
            throw IllegalStateException("Invalid Slurm configuration found for $ref", ex)
        }
    }

    private fun config(job: Job): SlurmConfiguration = config(job.specification.product)

    override suspend fun PluginContext.initialize(pluginConfig: ProductBasedConfiguration) {
        this@SlurmPlugin.pluginConfig.value = pluginConfig

        if (config.serverMode == ServerMode.Server) {
            for (handler in SlurmJobsIpcServer.handlers) {
                ipcServer.addHandler(handler)
            }
        }
    }

    override suspend fun PluginContext.create(resource: Job): FindByStringId? {
        val config = config(resource)
        val mountpoint = config.mountpoint
        mkdir("${mountpoint}/${resource.id}", "0770".toUInt(8))

        val sbatch = createSbatchFile(this, resource, config)

        val pathToScript = "${mountpoint}/${resource.id}/job.sbatch"
        NativeFile.open(path = pathToScript, readOnly = false).writeText(sbatch)
        val slurmId = SlurmCommandLine.submitBatchJob(pathToScript)

        ipcClient.sendRequest(SlurmJobsIpc.create, SlurmJob(resource.id, slurmId, config.partition))
        return null
    }

    override suspend fun PluginContext.terminate(resource: Job) {
        // NOTE(Dan): Everything else should be handled by the monitoring loop
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(resource.id))
        SlurmCommandLine.cancelJob(slurmJob.partition, slurmJob.slurmId)
    }

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

    override suspend fun PluginContext.extend(request: JobsProviderExtendRequestItem) {
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override suspend fun PluginContext.suspendJob(request: JobsProviderSuspendRequestItem) {
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override suspend fun PluginContext.runMonitoringLoop() {
        if (config.serverMode != ServerMode.Server) return

        while (true) {
            delay(5000)

            val jobs = SlurmJobMapper.browse(SlurmBrowseFlags(filterIsActive = true))
            log.debug("Jobs are: $jobs")
            if (jobs.isEmpty()) continue

            val acctJobs = SlurmCommandLine.browseJobAllocations(jobs.map { it.slurmId })
            log.debug("Allocations are: $acctJobs")

            // TODO(Dan): Everything should be more or less ready to do in bulk if needed
            acctJobs.forEach { job ->
                val dbState = jobs.first { it.slurmId == job.jobId }.lastKnown
                val ucloudId = jobs.first { it.slurmId == job.jobId }.ucloudId
                val uState = Mapping.uCloudStates[job.state] ?: return@forEach

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
                        SlurmJobMapper.updateState(
                            listOf(job.jobId),
                            listOf(uState.state)
                        )
                    } else {
                        log.warn("Caught an exception while updating job status: ${updateResponse}")
                    }
                }
            }

            val finishedJobs = acctJobs.mapNotNull { job ->
                val ucloudId = jobs.first { it.slurmId == job.jobId }.ucloudId
                val uState = Mapping.uCloudStates[job.state]

                if (uState!!.isFinal) {
                    val start = Instant.parse(job.start.plus("Z"))
                    val end = Instant.parse(job.end.plus("Z"))
                    val lastTs = Clock.System.now().toString()
                    val uDuration = run { end - start }.let { SimpleDuration.fromMillis(it.inWholeMilliseconds) }

                    val chargeResponse = JobsControl.chargeCredits.call(
                        bulkRequestOf(
                            ResourceChargeCredits(
                                ucloudId,
                                lastTs,
                                // TODO(Dan): This is not always true. It depends on the product's payment model.
                                ceil(uDuration.toMillis() / (1000L * 60.0)).toLong(),
                            )
                        ),
                        rpcClient
                    )

                    if (chargeResponse !is IngoingCallResponse.Ok<*, *>) {
                        log.warn("Caught an exception while charging credits for job: ${ucloudId}\n\t${chargeResponse}")
                    }

                    job.jobId
                } else null
            }

            if (finishedJobs.isEmpty()) continue

            SlurmJobMapper.markAsInactive(finishedJobs)
        }
    }


    override suspend fun ComputePlugin.FollowLogsContext.follow(job: Job) {

        val config = config(job)
        class OutputFile(val rank: Int, val out: NativeFile, val err: NativeFile)

        // TBD: error handling or move below
        val fdList by lazy { 
            (0..job.specification.replicas-1).map{ rank -> 
                OutputFile( 
                            rank, 
                            NativeFile.open(path = "${config.mountpoint}/${job.id}/std-${rank}.out", readOnly = true), 
                            NativeFile.open(path = "${config.mountpoint}/${job.id}/std-${rank}.err", readOnly = true)
                )
            }
        }

        try {
            // NOTE(Dan): Don't open the files until the job looks like it is running
            var currentStatus = getStatus(job.id)

            while (currentStatus.state != JobState.RUNNING && !currentStatus.isFinal) {
                delay(5000)
            }

            // NOTE(Dan): If the job is done, then don't attempt to read the logs
            if (currentStatus.isFinal) return


            while (isActive()) {

                val check: UcloudStateInfo = getStatus(job.id)
                if (check.state == JobState.RUNNING) {

                    fdList.forEach{ file ->
                        val line = file.out.readText(autoClose = false)
                        if (line.isNotEmpty()) emitStdout(file.rank, line)

                        val err = file.err.readText(autoClose = false)
                        if (err.isNotEmpty()) emitStderr(file.rank, err)
                    }

                } else break

                sleep(5)
            }

        } finally {

            fdList.forEach{ file -> 
                file.out?.close()
                file.err?.close()
            }

        }
    }

    private suspend fun PluginContext.getStatus(id: String): UcloudStateInfo {
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(id))

        val allocationInfo = SlurmCommandLine.browseJobAllocations(listOf(slurmJob.slurmId)).firstOrNull()
            ?: throw RPCException("Unknown Slurm Job Status: $id ($slurmJob)", HttpStatusCode.InternalServerError)

        return Mapping.uCloudStates.getOrElse(allocationInfo.state) {
            throw RPCException("Unknown Slurm State: $allocationInfo", HttpStatusCode.InternalServerError)
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
        val session:InteractiveSession = ipcClient.sendRequest(SlurmSessionIpc.retrieve, FindByStringId(request.sessionIdentifier) )
        println("MYSESSIONIS $session")
        return request.sessionIdentifier == session.token
    }

    override suspend fun ComputePlugin.ShellContext.handleShellSession(request: ShellRequest.Initialize) {
        // DONE Start SSH session
        //Initialize(sessionIdentifier=testing, cols=94, rows=35)

        val session:InteractiveSession = ipcClient.sendRequest(SlurmSessionIpc.retrieve, FindByStringId(request.sessionIdentifier) )
        val slurmJob = ipcClient.sendRequest(SlurmJobsIpc.retrieve, FindByStringId(session.ucloudId))
        val nodes:Map<Int,String> = SlurmCommandLine.getJobNodeList(slurmJob.slurmId)


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


        val pStatus:ProcessStatus = process.retrieveStatus(false)

        //process!!.stdin!!.write(" \n stty cols $cols rows $rows \n ".encodeToByteArray())
        val buffer = ByteArray(4096)


        readloop@ while (  isActive() && pStatus.isRunning ) {
            val userInput = receiveChannel.tryReceive().getOrNull()
            when (userInput) {
                is ShellRequest.Input -> {
                    // Forward input to SSH session
                    //println("USERINPUT: ${ userInput.data }")
                    process!!.stdin!!.write(  userInput.data.encodeToByteArray()  )
                }

                is ShellRequest.Resize -> {
                    // Send resize event to SSH session
                    println("RESIZE EVENT: ${ userInput } ")
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

            // NOTE(Dan): Function doesn't exist yet
            /*
            val sshData = ssh.pollData()
            if (sshData != null) {
                emitData(sshData)
            }
             */


            val bytesRead:ReadResult = process.stdout!!.read(buffer)
            if (bytesRead.isError )  {

                when(bytesRead.getErrorOrThrow()) {
                    platform.posix.EAGAIN -> {
                        delay(15)
                        continue@readloop
                    }
                    else -> break@readloop
                }
                
            } 

            if (! bytesRead.isEof) {
                val decodedString = buffer.decodeToString(0, bytesRead.getOrThrow() )
                emitData(decodedString)
            }
            
            delay(15)
        }

        if (! isActive() ) kill(process.pid, SIGINT) 
        //TODO: investigate
        // testuser     244       1  0 12:54 ?        00:00:00 [sshd] <defunct>
        // testuser     245       1  0 12:54 ?        00:00:00 [bash] <defunct>
        // testuser     282       1  0 12:54 ?        00:00:00 [bash] <defunct>

    
    }

    companion object {
        private val log = Log("SlurmPlugin")
    }
}
