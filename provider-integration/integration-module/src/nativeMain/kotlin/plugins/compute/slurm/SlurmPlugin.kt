package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.BulkResponse
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
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import platform.posix.ceil
import platform.posix.mkdir
import platform.posix.sleep
import kotlin.native.concurrent.AtomicReference

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

        val sbatch = createSbatchFile(resource, config)

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
        var stdout: NativeFile? = null
        var stderr: NativeFile? = null
        try {
            // NOTE(Dan): Don't open the files until the job looks like it is running
            var currentStatus = getStatus(job.id)

            while (currentStatus.state != JobState.RUNNING && !currentStatus.isFinal) {
                delay(5000)
            }

            // NOTE(Dan): If the job is done, then don't attempt to read the logs
            if (currentStatus.isFinal) return

            stdout = NativeFile.open(path = "${config.mountpoint}/${job.id}/std.out", readOnly = true)
            stderr = NativeFile.open(path = "${config.mountpoint}/${job.id}/std.err", readOnly = true)

            while (isActive()) {
                val check: UcloudStateInfo = getStatus(job.id)

                if (check.state == JobState.RUNNING) {
                    val line = stdout.readText(autoClose = false)
                    if (line.isNotEmpty()) emitStdout(0, line)

                    val err = stderr.readText(autoClose = false)
                    if (err.isNotEmpty()) emitStderr(0, err)

                } else break

                sleep(5)
            }

        } finally {
            stdout?.close()
            stderr?.close()
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
        return OpenSession.Shell(job.job.id, job.rank, "testing")
    }

    override suspend fun PluginContext.canHandleShellSession(request: ShellRequest.Initialize): Boolean {
        return request.sessionIdentifier == "testing"
    }

    override suspend fun ComputePlugin.ShellContext.handleShellSession(cols: Int, rows: Int) {
        // TODO Start SSH session

        while (isActive()) {
            val userInput = receiveChannel.tryReceive().getOrNull()
            when (userInput) {
                is ShellRequest.Input -> {
                    // Forward input to SSH session
                    emitData(userInput.data)
                }

                is ShellRequest.Resize -> {
                    // Send resize event to SSH session
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

            delay(15)
        }
    }

    companion object {
        private val log = Log("SlurmPlugin")
    }
}
