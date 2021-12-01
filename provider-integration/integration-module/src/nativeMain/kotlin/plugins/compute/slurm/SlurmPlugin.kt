package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.ProductBasedConfiguration
import dk.sdu.cloud.ProductReferenceWithoutProvider
import dk.sdu.cloud.ServerMode
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits
import dk.sdu.cloud.app.orchestrator.api.ComputeSupport
import dk.sdu.cloud.app.orchestrator.api.CpuAndMemory
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobUpdate
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.JobsProviderExtendRequestItem
import dk.sdu.cloud.app.orchestrator.api.JobsProviderSuspendRequestItem
import dk.sdu.cloud.app.orchestrator.api.JobsProviderUtilizationResponse
import dk.sdu.cloud.app.orchestrator.api.QueueStatus
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.freeze
import dk.sdu.cloud.ipc.JsonRpcRequest
import dk.sdu.cloud.ipc.orThrow
import dk.sdu.cloud.ipc.sendRequestBlocking
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.ipcServer
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withTransaction
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.ProcessResultText
import dk.sdu.cloud.utils.executeCommandToText
import dk.sdu.cloud.utils.readText
import dk.sdu.cloud.utils.writeText
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import platform.posix.ceil
import platform.posix.mkdir
import platform.posix.sleep
import kotlin.native.concurrent.AtomicReference

import dk.sdu.cloud.app.orchestrator.api.JobsProviderOpenInteractiveSessionRequestItem
import dk.sdu.cloud.app.orchestrator.api.OpenSession
import dk.sdu.cloud.app.orchestrator.api.JobsOpenInteractiveSessionRequestItem
import dk.sdu.cloud.app.orchestrator.api.InteractiveSessionType
import dk.sdu.cloud.utils.secureToken

@Serializable
data class SlurmConfiguration(
    val partition: String,
    val mountpoint: String,
    val useFakeMemoryAllocations: Boolean = false
)

class SlurmPlugin : ComputePlugin {
    private val pluginConfig = AtomicReference<ProductBasedConfiguration?>(null).also { it.freeze() }

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
            for (handler in Handlers.ipc) {
                ipcServer.addHandler(handler)
            }
        }
    }

    override suspend fun PluginContext.create(resource: Job): FindByStringId? {
        val config = config(resource)
        val mountpoint = config.mountpoint
        mkdir("${mountpoint}/${resource.id}", "0770".toUInt(8))

        val sbatch = createSbatchFile(resource, config)

        NativeFile.open(path = "${mountpoint}/${resource.id}/job.sbatch", readOnly = false).writeText(sbatch)

        val (code, stdout, stderr) = executeCommandToText(SBATCH_EXE) {
            addArg("${mountpoint}/${resource.id}/job.sbatch")
            addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)
        }

        if (code != 0) throw RPCException(
            "Unhandled exception when creating job: $stderr",
            HttpStatusCode.BadRequest
        )

        var slurmId = stdout

        ipcClient.sendRequestBlocking(
            JsonRpcRequest(
                "slurm.jobs.create",
                SlurmJob(
                    resource.id,
                    slurmId.trim(),
                    config.partition
                ).toJson()
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


        var currentStatus = getStatus(resource.id)
        while (currentStatus.state != JobState.RUNNING && !currentStatus.isFinal ) {
            delay(5000)
        }


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

        return null
    }

    override suspend fun PluginContext.terminate(resource: Job) {
        println("delete job")

        val config = config(resource)

        val slurmJob: SlurmJob = ipcClient.sendRequestBlocking(
            JsonRpcRequest(
                "slurm.jobs.retrieve",
                SlurmJob(resource.id, "someid").toJson()
            )
        ).orThrow<SlurmJob>()


        // Sends SIGKILL or custom with -s INTEGER
        val (code, stdout, stderr) = executeCommandToText(SCANCEL_EXE) {
            addArg("--partition", config.partition)
            addArg("--full")
            addArg("${slurmJob.slurmId}")
            addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)
        }

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

        //TODO: mark job inactive in db
    }

    override suspend fun PluginContext.retrieveClusterUtilization(): JobsProviderUtilizationResponse {
        // squeue --format '%A|%m|%C|%T' --noheader --states running,pending --noconvert
        // 26|50M|1|PENDING
        // 27|50M|1|PENDING

        //get pending cpu/mem jobs
        val (_, jobs, _) = executeCommandToText(SQUEUE_EXE) {
            addArg("--format", "%A|%m|%C|%T")
            addArg("--noheader")
            addArg("--noconvert")
            addArg("--states", "running,pending")
            addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)
        }

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
        val (_, nodes, _) = executeCommandToText(SINFO_EXE) {
            addArg("--format", "%n|%c|%m")
            addArg("--noheader")
            addArg("--noconvert")
            addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)
        }

        val nList = nodes.lines().map {
            it.trimIndent()
            it.trim()
            it.split("|")
        }.toList()

        var clusterCpu = 0;
        var clusterMem = 0;

        nList.forEach { line ->
            clusterCpu += line[1].toInt()
            clusterMem += line[2].replace("M", "").toInt()
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
        if (config.serverMode != ServerMode.Server) return

        while (true) {
            println("RunMonitoringLoop")
            sleep(5)
            continue

            val jobs: MutableList<SlurmJob> = mutableListOf()

            //Get all db active jobs
            dbConnection.withTransaction { connection ->
                connection.prepareStatement(
                    """
                        select ucloud_id, local_id, partition, lastknown, status
                        from job_mapping 
                        where status = 1
                    """
                ).useAndInvoke(
                    readRow = {
                        jobs.add(
                            SlurmJob(
                                it.getString(0)!!,
                                it.getString(1)!!,
                                it.getString(2)!!,
                                it.getString(3)!!,
                                it.getInt(4)!!
                            )
                        )
                    }
                )
            }

            if (jobs.isEmpty()) continue

            // println(jobs)
            val (_, stdout, _) = executeCommandToText(SACCT_EXE) {
                addArg("--jobs", jobs.joinToString(",") { it.slurmId })
                addArg("--allusers") // Displays all users' jobs when run by user root or if PrivateData is not configured to jobs. Otherwise display the current user's jobs
                addArg("--format", "jobid,state,exitcode,start,end")
                addArg("--allocations") // Only show statistics relevant to the job allocation itself, not taking steps into consideration.
                addArg("--parsable2")
                addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)
            }

            val acctStdLines = stdout.lines().mapNotNull { line -> line.split("|") }
            // create map then list of objects
            val header = acctStdLines[0]
            val acctJobs =
                acctStdLines
                    .mapIndexedNotNull { idx, line ->   // [{Start=2021-11-03T12:16:36, State=TIMEOUT, ExitCode=0:0, End=2021-11-03T12:21:46, JobID=33}, {Start=2021-11-03T12:16:36, State=TIMEOUT, ExitCode=0:0, End=2021-11-03T12:21:46, JobID=34}]
                        val map: MutableMap<String, String> = HashMap()
                        line.forEachIndexed { idx, word ->
                            map[header[idx]] = word
                        }
                        if (idx > 0) map else null
                    }
                    .mapNotNull { map ->                // [acctEntry(jobId=33, state=TIMEOUT, exitCode=0:0, start=2021-11-03T12:16:36, end=2021-11-03T12:21:46), acctEntry(jobId=34, state=TIMEOUT, exitCode=0:0, start=2021-11-03T12:16:36, end=2021-11-03T12:21:46)]
                        AcctEntry(map["JobID"], map["State"]!!.split(" ")!!.getOrElse(0){""}, map["ExitCode"], map["Start"], map["End"])
                    }


            acctJobs.forEach { job ->
                val dbState = jobs.first { it.slurmId == job.jobId }.lastKnown
                val ucloudId = jobs.first { it.slurmId == job.jobId }.ucloudId
                val uState = Mapping.uCloudStates.get(job.state) //{ throw RPCException("Unknown Slurm Job Status", HttpStatusCode.BadRequest) }

                if (  uState!!.isFinal && !uState.providerState.equals(dbState)  ) {

                    //println("updating " + job)
                    JobsControl.update.call(
                        bulkRequestOf(
                            ResourceUpdateAndId(
                                ucloudId,
                                JobUpdate(uState.state, status = uState.message)
                            )
                        ),
                        rpcClient,
                    ).orThrow()

                    //update all finished jobs to status 0
                    dbConnection.withTransaction { connection ->
                        connection.prepareStatement(
                            """
                                update job_mapping 
                                set lastknown = :lastknown
                                where local_id = :local_id
                            """
                        ).useAndInvokeAndDiscard {
                            bindString("local_id", job.jobId!!)
                            bindString("lastknown", uState.state.toString())
                        }
                    }
                }
            }

            val finishedJobs = acctJobs.mapNotNull { job ->

                val ucloudId = jobs.first { it.slurmId == job.jobId }.ucloudId
                val uState = Mapping.uCloudStates.get(job.state) //{ throw RPCException("Unknown Slurm Job Status", HttpStatusCode.BadRequest) }

                if (uState!!.isFinal) {

                    val start = Instant.parse(job.start.plus("Z"))
                    val end = Instant.parse(job.end.plus("Z"))
                    val lastTs = Clock.System.now().toString()
                    val uDuration = run { end - start }.let { SimpleDuration.fromMillis(it.inWholeMilliseconds) }

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

                    job.jobId
                } else null
            }

            if (finishedJobs.isEmpty()) continue

            //TODO: replace with sqlite3_carray_bind
            dbConnection.withTransaction { connection ->
                connection.prepareStatement(
                    """
                        update job_mapping
                        set status = 0
                        where local_id in ( ${finishedJobs.mapIndexed { idx, _ -> ":job_id_${idx}" }.joinToString(",")} )
                    """
                ).useAndInvokeAndDiscard {
                    finishedJobs.forEachIndexed { index, s -> 
                        bindString("job_id_${index}", s)
                    }
                }
            }

        }
    }

    override suspend fun ComputePlugin.FollowLogsContext.follow(job: Job) {
        val config = config(job)
        var stdout: NativeFile? = null
        var stderr: NativeFile? = null
        try {
            // NOTE(Dan): Don't open the files until the job looks like it is running
            var currentStatus = getStatus(job.id)

            while (currentStatus.state != JobState.RUNNING && !currentStatus.isFinal ) {
                delay(5000)
            }

            // NOTE(Dan): If the job is done, then don't attempt to read the logs
            if (currentStatus.isFinal) return

            stdout = NativeFile.open(path = "${config.mountpoint}/${job.id}/std.out", readOnly = true)
            stderr = NativeFile.open(path = "${config.mountpoint}/${job.id}/std.err", readOnly = true)

            while (isActive()) {
                val check: UcloudStateInfo = getStatus(job.id)

                if ( check.state == JobState.RUNNING ) {

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

    private fun PluginContext.getStatus(id: String): UcloudStateInfo {
        val slurmJob: SlurmJob = ipcClient.sendRequestBlocking(
            JsonRpcRequest(
                "slurm.jobs.retrieve",
                SlurmJob(id, "someid").toJson()
            )
        ).orThrow()

        val (code, stdout, _) = executeCommandToText(SQUEUE_EXE) {
            addArg("--partition", slurmJob.partition)
            addArg("--jobs", slurmJob.slurmId)
            addArg("--noheader")
            addArg("--format", "%T")
            addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)
        }

        //if job information is removed due to MinJobAge then squeue will throw slurm_load_jobs error: Invalid job id specified . Need to also check sacct in this case
        val sacct = if (code != 0) {
            executeCommandToText(SACCT_EXE) {
                addArg("--partition", slurmJob.partition)
                addArg("--jobs", slurmJob.slurmId)
                addArg("--allusers")
                addArg("--format", "jobid,state,exitcode")
                addArg("--noheader")
                addArg("--allocations")
                addArg("--parsable2")
                addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)
            }
        } else {
            ProcessResultText(1, "", "")
        }

        val slurmStatus:String = if (code == 0) stdout.trim() else sacct.stdout.lines().get(0).split("|").get(1).trim().split(" ").get(0)
        val ucloudStateInfo = Mapping.uCloudStates.getOrElse(slurmStatus) { throw RPCException("Unknown Slurm Job Status", HttpStatusCode.BadRequest) }
        return ucloudStateInfo
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

    override suspend fun PluginContext.openInteractiveSession(jobRequest: JobsProviderOpenInteractiveSessionRequestItem): OpenSession {

        val job = jobRequest.job as Job
        val dbJob: SlurmJob = ipcClient.sendRequestBlocking( JsonRpcRequest( "slurm.jobs.retrieve", SlurmJob(job.id, "").toJson())).orThrow()
        val sessionId = secureToken(8)

        println("sessionId $sessionId")


        //request received      
        // JobsProviderOpenInteractiveSessionRequestItem(

        // job=Job(id=16, owner=ResourceOwner(createdBy=user, project=null), updates=[], specification=JobSpecification(application=alpine@3, product=ProductReference(id=im1-1, category=im1, provider=development), name=qkhjhfggdsfsdfs, replicas=1, allowDuplicateJob=false, parameters={}, resources=[], timeAllocation=00:15:00), status=JobStatus(state=RUNNING, jobParametersJson=ExportedParameters(siteVersion=3, request=ExportedParametersRequest(application=alpine@3, product=ProductReference(id=im1-1, category=im1, provider=development), name=qkhjhfggdsfsdfs, replicas=1, parameters={}, resources=[], timeAllocation=00:15:00, resolvedProduct=null, resolvedApplication=null, resolvedSupport=null, allowDuplicateJob=true), machineType={"cpu":1,"memoryInGigs":1}), startedAt=1638348131822, expiresAt=1638349031822, resolvedApplication=Application(metadata=ApplicationMetadata(name=alpine, version=3, authors=[Dan], title=Alpine, description=Alpine!
        // , website=null, public=false), invocation=ApplicationInvocationDescription(tool=ToolReference(name=alpine, version=3, tool=Tool(owner=admin@dev, createdAt=1638179646070, modifiedAt=1638179646070, description=NormalizedToolDescription(info=alpine@1, container='alpine:3'))), invocation=[WordInvocationParameter(word=sh), WordInvocationParameter(word=-c), WordInvocationParameter(word=echo "Hello, World!"; sleep 2; echo "How are you doing?"; sleep 1; echo "This is just me writing some stuff for testing purposes!"; sleep 1; seq 0 7200 | xargs -n 1 -I _ sh -c 'echo _; sleep 1';
        // )], parameters=[], outputFileGlobs=[*, stdout.txt, stderr.txt], applicationType=BATCH, vnc=null, web=null, container=ContainerDescription(changeWorkingDirectory=true, runAsRoot=true, runAsRealUser=false), environment=null, allowAdditionalMounts=null, allowAdditionalPeers=null, allowMultiNode=true, fileExtensions=[], licenseServers=[])), resolvedSupport=null, resolvedProduct=null), createdAt=1638348129605, output=JobOutput(outputFolder=null), permissions=ResourcePermissions(myself=[ADMIN], others=null))

        // , rank=0, sessionType=SHELL)

//create interactive ssh process and get pid





        return OpenSession.Shell(job.id, 0, sessionId )

    }






    companion object {
        const val SBATCH_EXE = "/usr/bin/sbatch"
        const val SCANCEL_EXE = "/usr/bin/scancel"
        const val SQUEUE_EXE = "/usr/bin/squeue"
        const val SINFO_EXE = "/usr/bin/sinfo"
        const val SACCT_EXE = "/usr/bin/sacct"
        const val SLURM_CONF_KEY = "SLURM_CONF"
        const val SLURM_CONF_VALUE = "/etc/slurm/slurm.conf"
    }
}
