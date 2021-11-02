package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue.Text
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ipc.IpcHandler
import dk.sdu.cloud.ipc.JsonRpcRequest
import dk.sdu.cloud.ipc.orThrow
import dk.sdu.cloud.ipc.sendRequestBlocking
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withTransaction
import dk.sdu.cloud.utils.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import platform.posix.ceil
import platform.posix.mkdir
import platform.posix.sleep

val TAG = "slurm"

class SlurmPlugin : ComputePlugin {
    private fun PluginContext.retrieveSlurmStatus(id: String): SlurmStatus {
        val slurmJob: SlurmJob = ipcClient.sendRequestBlocking(
            JsonRpcRequest(
                "slurm.jobs.retrieve",
                defaultMapper.encodeToJsonElement(SlurmJob(id, "someid", "normal", 1)) as JsonObject
            )
        ).orThrow()

        val (code, stdout, _) = CommandBuilder("/usr/bin/squeue")
            .addArg("--partition", slurmJob.partition)
            .addArg("--jobs", slurmJob.slurmId)
            .addArg("--noheader")
            .addArg("--format", "%T")
            .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf")
            .executeToText()

        //if job information is removed due to MinJobAge then squeue will throw slurm_load_jobs error: Invalid job id specified . Need to also check sacct in this case
        val sacct = if (code != 0) {
            CommandBuilder("/usr/bin/sacct")
                .addArg("--partition", slurmJob.partition)
                .addArg("--jobs", slurmJob.slurmId)
                .addArg("--allusers")
                .addArg("--format", "jobid,state,exitcode")
                .addArg("--noheader")
                .addArg("--completion")
                .addArg("--parsable2")
                .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf")
                .executeToText()
        } else {
            ProcessResultText(1, "", "")
        }

        val slurmStatus = if (code == 0) stdout.trim() else sacct.stdout.lines().get(0).split("|").get(1)

        val imStatus = when (slurmStatus) {
            "PENDING", "CONFIGURING", "RESV_DEL_HOLD", "REQUEUE_FED", "SUSPENDED" -> SlurmStatus(
                id,
                JobState.IN_QUEUE,
                slurmStatus,
                "Job is queued"
            )
            "REQUEUE_HOLD" -> SlurmStatus(id, JobState.IN_QUEUE, slurmStatus, "Job is held for requeue")
            "REQUEUED" -> SlurmStatus(id, JobState.IN_QUEUE, slurmStatus, "Job is requeued")
            "RESIZING" -> SlurmStatus(id, JobState.IN_QUEUE, slurmStatus, "Job is resizing")
            "RUNNING", "COMPLETING", "SIGNALING", "SPECIAL_EXIT", "STAGE_OUT" -> SlurmStatus(
                id,
                JobState.RUNNING,
                slurmStatus,
                "Job is running"
            )
            "STOPPED" -> SlurmStatus(id, JobState.RUNNING, slurmStatus, "Job is stopped")
            "COMPLETED", "CANCELLED", "FAILED" -> SlurmStatus(id, JobState.SUCCESS, slurmStatus, "Job is success")
            "OUT_OF_MEMORY" -> SlurmStatus(id, JobState.SUCCESS, slurmStatus, "Out of memory")
            "BOOT_FAIL", "NODE_FAIL" -> SlurmStatus(id, JobState.FAILURE, slurmStatus, "Job is failed")
            "REVOKED" -> SlurmStatus(id, JobState.FAILURE, slurmStatus, "Job is revoked")
            "PREEMPTED" -> SlurmStatus(id, JobState.FAILURE, slurmStatus, "Preempted")
            "DEADLINE", "TIMEOUT" -> SlurmStatus(id, JobState.EXPIRED, slurmStatus, "Job is expired")
            else -> throw RPCException("Unknown Slurm Job Status", HttpStatusCode.BadRequest)
        }

        return imStatus
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

    override suspend fun PluginContext.initialize() {
        val log = Log("SlurmComputePlugin")

        ipcServer.addHandler(
            IpcHandler("slurm.jobs.create") { user, jsonRequest ->
                log.debug("Asked to add new job mapping!")
                val req = runCatching {
                    defaultMapper.decodeFromJsonElement<SlurmJob>(jsonRequest.params)
                }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                //println(req)

                dbConnection.withTransaction { connection ->

                    connection.prepareStatement(
                        """
                                insert into job_mapping (local_id, ucloud_id, partition, status) values ( :local_id, :ucloud_id, :partition, :status )
                            """
                    ).useAndInvokeAndDiscard {
                        bindString("ucloud_id", req.ucloudId)
                        bindString("local_id", req.slurmId)
                        bindString("partition", req.partition)
                        bindInt("status", req.status)
                    }
                }

                JsonObject(emptyMap())
            }
        )

        ipcServer.addHandler(
            IpcHandler("slurm.jobs.retrieve") { user, jsonRequest ->
                log.debug("Asked to get job!")
                val req = runCatching {
                    defaultMapper.decodeFromJsonElement<SlurmJob>(jsonRequest.params)
                }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                var slurmJob: SlurmJob? = null

                dbConnection.withTransaction { connection ->

                    connection.prepareStatement(
                        """
                                select * 
                                from job_mapping 
                                where ucloud_id = :ucloud_id
                            """
                    ).useAndInvoke(
                        prepare = { bindString("ucloud_id", req.ucloudId) },
                        readRow = {
                            slurmJob = SlurmJob(it.getString(0)!!, it.getString(1)!!, it.getString(2)!!, it.getInt(3)!!)
                        }
                    )
                }

                //println(" DATABASE RESULT $slurmJob")

                defaultMapper.encodeToJsonElement(slurmJob) as JsonObject
            }
        )

        ipcServer.addHandler(
            IpcHandler("slurm.jobs.browse") { user, jsonRequest ->
                log.debug("Asked to browse jobs!")
                // val req = runCatching {
                //     defaultMapper.decodeFromJsonElement<SlurmJob>(jsonRequest.params)
                // }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                val jobs: MutableList<SlurmJob> = mutableListOf()

                dbConnection.withTransaction { connection ->
                    connection.prepareStatement(
                        """
                                select * 
                                from job_mapping 
                                where status = 1
                            """
                    ).useAndInvoke(
                        readRow = {
                            jobs.add(SlurmJob(it.getString(0)!!, it.getString(1)!!, it.getString(2)!!, it.getInt(3)!!))
                        }
                    )
                }

                println(" DATABASE RESULT $jobs")

                defaultMapper.encodeToJsonElement(jobs) as JsonObject
            }
        )
    }

    override suspend fun PluginContext.create(resource: Job): FindByStringId? {
        val mountpoint = config.plugins!!.compute!!.plugins!!.first { it.id == TAG }!!.configuration!!.mountpoint
        mkdir("${mountpoint}/${resource.id}", "0770".toUInt(8))

        val job_content =
            resource.specification?.parameters?.getOrElse("file_content") { throw Exception("no file_content") } as Text
        val sbatch_content = manageHeader(resource, config)

        NativeFile.open(path = "${mountpoint}/${resource.id}/job.req", readOnly = false).writeText(job_content.value)
        NativeFile.open(path = "${mountpoint}/${resource.id}/job.sbatch", readOnly = false).writeText(sbatch_content)

        runBlocking {

            val (code, stdout, stderr) = CommandBuilder("/usr/bin/sbatch").addArg("${mountpoint}/${resource.id}/job.sbatch")
                .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf").executeToText()
            if (code != 0) throw RPCException(
                "Unhandled exception when creating job: $stderr",
                HttpStatusCode.BadRequest
            )

            var slurmId = stdout

            val job_partition =
                config.plugins?.compute?.plugins?.first { it.id == TAG }?.configuration?.partition.toString()

            ipcClient.sendRequestBlocking(
                JsonRpcRequest(
                    "slurm.jobs.create",
                    defaultMapper.encodeToJsonElement(
                        SlurmJob(
                            resource.id,
                            slurmId.trim(),
                            job_partition,
                            1
                        )
                    ) as JsonObject
                )
            ).orThrow<Unit>()

            sleep(2)

            JobsControl.update.call(
                bulkRequestOf(
                    ResourceUpdateAndId(
                        resource.id,
                        JobUpdate(
                            JobState.IN_QUEUE,
                            status = "The job has been queued"
                        )
                    )
                ),
                rpcClient
            ).orThrow()

            sleep(5)

            JobsControl.update.call(
                bulkRequestOf(
                    ResourceUpdateAndId(
                        resource.id,
                        JobUpdate(
                            JobState.RUNNING,
                            status = "The job is now running"
                        )
                    )
                ),
                rpcClient
            ).orThrow()
        }

        return null
    }

    override suspend fun PluginContext.terminate(resource: Job) {
        println("delete job")

        val job_partition = config.plugins?.compute?.plugins?.first { it.id == TAG }?.configuration?.partition

        val slurmJob: SlurmJob = ipcClient.sendRequestBlocking(
            JsonRpcRequest(
                "slurm.jobs.retrieve",
                defaultMapper.encodeToJsonElement(SlurmJob(resource.id, "someid", "normal", 1)) as JsonObject
            )
        ).orThrow<SlurmJob>()


        // Sends SIGKILL or custom with -s INTEGER
        val (code, stdout, stderr) = CommandBuilder("/usr/bin/scancel")
            .addArg("--partition", job_partition)    // ${job_partition}
            .addArg("--full")
            .addArg("${slurmJob.slurmId}")
            .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf")
            .executeToText()

        sleep(2)
        runBlocking {
            JobsControl.update.call(
                bulkRequestOf(
                    ResourceUpdateAndId(
                        resource.id,
                        JobUpdate(
                            JobState.SUCCESS,
                            status = "Shutting down job"
                        )
                    )
                ),
                rpcClient
            ).orThrow()
        }

        //TODO: mark job inactive in db
    }

    override suspend fun PluginContext.retrieveClusterUtilization(): JobsProviderUtilizationResponse {
        // squeue --format '%A|%m|%C|%T' --noheader --states running,pending --noconvert
        // 26|50M|1|PENDING
        // 27|50M|1|PENDING

        //get pending cpu/mem jobs
        val (_, jobs, _) = CommandBuilder("/usr/bin/squeue")
            .addArg("--format", "%A|%m|%C|%T")
            .addArg("--noheader")
            .addArg("--noconvert")
            .addArg("--states", "running,pending")
            .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf")
            .executeToText()

        val mList = jobs.lines().map {
            it.trimIndent()
            it.trim()
            it.split("|")
        }.toList()

        var usedCpu = 0
        var usedMem = 0
        var pendingJobs = 0
        var runningJobs = 0

        mList.forEach { line ->

            if (line[3].equals("PENDING")) {
                pendingJobs++
            }

            if (line[3].equals("RUNNING")) {
                usedCpu = usedCpu + line[2].toInt()
                usedMem = usedMem + line[1].replace("M", "").toInt()
                runningJobs++
            }

        }

        //println("$usedCpu $usedMem $pendingJobs $runningJobs")


        // sinfo --format='%n|%c|%m' --noconvert --noheader
        // c1|1|1000
        // c2|1|1000

        //get cluster overall cpu/mem
        val (_, nodes, _) = CommandBuilder("/usr/bin/sinfo")
            .addArg("--format", "%n|%c|%m")
            .addArg("--noheader")
            .addArg("--noconvert")
            .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf")
            .executeToText()

        val nList = nodes.lines().map {
            it.trimIndent()
            it.trim()
            it.split("|")
        }.toList()

        var clusterCpu = 0;
        var clusterMem = 0;

        nList.forEach { line ->
            clusterCpu = clusterCpu + line[1].toInt()
            clusterMem = clusterMem + line[2].replace("M", "").toInt()
        }

        //println("$clusterCpu $clusterMem")

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
        println("Suspending job!")
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override suspend fun PluginContext.runMonitoringLoop() {
        Log("RunMonitoringLoop")

        while (true) {
            println("RunMonitoringLoop")
            sleep(5)

            val terminalStates = listOf(
                "COMPLETED",
                "CANCELLED",
                "FAILED",
                "OUT_OF_MEMORY",
                "BOOT_FAIL",
                "NODE_FAIL",
                "PREEMPTED",
                "REVOKED",
                "DEADLINE",
                "TIMEOUT"
            )
            val jobs: MutableList<SlurmJob> = mutableListOf()

            //Get all db active jobs
            dbConnection.withTransaction { connection ->
                connection.prepareStatement(
                    """
                        select * 
                        from job_mapping 
                        where status = 1
                    """
                ).useAndInvoke(
                    readRow = {
                        jobs.add(SlurmJob(it.getString(0)!!, it.getString(1)!!, it.getString(2)!!, it.getInt(3)!!))
                    }
                )
            }

            // --ids 7,8 ...
            val ids = jobs.fold("", { acc, item -> acc + item.slurmId + "," })


            //println(ids)
            val (_, stdout, _) = CommandBuilder("/usr/bin/sacct")
                .addArg("--jobs", ids)
                .addArg("--allusers")
                .addArg("--format", "jobid,state,exitcode,start,end")
                .addArg("--noheader")
                .addArg("--parsable2")
                .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf")
                .executeToText()
            //println(stdout.lines() )

            val acctStdLines = if (!stdout.trim().isEmpty()) stdout.lines() else continue

            // take job completion info not just batch ie TIMEOUT
            val acctJobs: List<String> = acctStdLines.fold(emptyList(), { acc, line ->
                if (!line.split("|").get(0).contains(".") && !line.isEmpty()) {
                    acc + line
                } else {
                    acc
                }
            })

            acctJobs.forEach { job ->
                val state = job.toString().split("|").get(1)

                if (state in terminalStates) {

                    val thisId = job.toString().split("|").get(0)
                    val ucloudId = jobs.first { it.slurmId == thisId }.ucloudId

                    val start = Instant.parse(job.toString().split("|").get(3).plus("Z"))
                    val end = Instant.parse(job.toString().split("|").get(4).plus("Z"))
                    val lastTs = Clock.System.now().toString()
                    val uDuration = run { end - start }.let { SimpleDuration.fromMillis(it.inWholeMilliseconds) }

                    println("JOBID: " + ucloudId + " " + lastTs + " " + uDuration)

                    runBlocking {
                        JobsControl.chargeCredits.call(
                            bulkRequestOf(
                                ResourceChargeCredits(
                                    ucloudId,
                                    lastTs,
                                    // TODO(Dan): This is not always true. It depends on the product's payment model.
                                    ceil(uDuration.toMillis() / (1000L * 60.0)).toLong(),
                                )
                            ),
                            rpcClient
                        ).orThrow()
                    }
                    //TODO: update table job_mapping.status = 0 where slurmId in ( list )
                }
            }
        }
    }

    override suspend fun ComputePlugin.FollowLogsContext.follow(job: Job) {
        sleep(2)
        println("follow logs")

        val mountpoint = config.plugins.compute!!.plugins.first { it.id == TAG }.configuration!!.mountpoint

        val stdOut = NativeFile.open(path = "${mountpoint}/${job.id}/std.out", readOnly = true)
        val stdErr = NativeFile.open(path = "${mountpoint}/${job.id}/std.err", readOnly = true)

        thisLoop@ while (isActive()) {
            val mState: SlurmStatus = retrieveSlurmStatus(job.id)
            when (mState.ucloudState) {
                JobState.RUNNING -> {
                    val line = stdOut.readText(autoClose = false)
                    if (!line.isEmpty()) emitStdout(0, line)

                    val err = stdErr.readText(autoClose = false)
                    if (!err.isEmpty()) emitStderr(0, err)
                }

                else -> {
                    JobsControl.update.call(
                        bulkRequestOf(
                            ResourceUpdateAndId(
                                mState.id,
                                JobUpdate(mState.ucloudState, status = mState.message)
                            )
                        ),
                        rpcClient
                    ).orThrow()

                    break@thisLoop
                }
            }

            sleep(5)
        }
    }
}
