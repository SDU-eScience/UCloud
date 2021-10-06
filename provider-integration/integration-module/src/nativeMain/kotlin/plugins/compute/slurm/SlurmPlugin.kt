package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.app.orchestrator.api.*

import dk.sdu.cloud.app.store.api.AppParameterValue.Text
import dk.sdu.cloud.accounting.api.ProductReference

import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.plugins.*
import kotlinx.coroutines.runBlocking
import platform.posix.*
import kotlinx.cinterop.*
import dk.sdu.cloud.utils.*

import dk.sdu.cloud.sql.*
import sqlite3.*

import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.ipc.*


import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

import io.ktor.http.*
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper

import platform.posix.mkdir

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement

import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker

import dk.sdu.cloud.app.store.api.SimpleDuration



@Serializable
data class SlurmJob(
    val ucloudId: String,
    val slurmId: String,
    val partition: String = "normal",
    val status: Int = 1
)


typealias UcloudState =  JobState
data class Status ( val id:String, val ucloudStatus: UcloudState, val slurmStatus: String, val message: String  )    


fun manageHeader(job:Job, config: IMConfiguration ):String { 
 
        val job_content = job.specification?.parameters?.getOrElse("file_content") { throw Exception("no file_content") } as Text
        val job_timelimit = "${job.specification?.timeAllocation?.hours}:${job.specification?.timeAllocation?.minutes}:${job.specification?.timeAllocation?.seconds}" 
        val request_product = job.specification?.product as ProductReference

        val job_partition = config.plugins?.compute?.plugins?.first{ it.id == "slurm"  }?.configuration?.partition
        val job_nodes = job.specification!!.replicas

        val product_cpu = config.plugins?.compute?.products?.first{ it.id == request_product.id  }?.cpu.toString()
        val product_mem = config.plugins?.compute?.products?.first{ it.id == request_product.id  }?.mem.toString()
        val product_gpu = config.plugins?.compute?.products?.first{ it.id == request_product.id  }?.gpu.toString()


    //sbatch will stop processing further #SBATCH directives once the first non-comment non-whitespace line has been reached in the script.
    // remove whitespaces
    var fileBody = job_content.value.lines().map{it.trim()}.toMutableList()
    val headerSuffix = 
                        """
                            #
                            # POSTFIX START
                            #
                            #SBATCH --chdir /data/${job.id} 
                            #SBATCH --cpus-per-task ${product_cpu} 
                            #SBATCH --mem ${product_mem} 
                            #SBATCH --gpus-per-node ${product_gpu} 
                            #SBATCH --time ${job_timelimit} 
                            #SBATCH --nodes ${job_nodes} 
                            #SBATCH --job-name ${job.id} 
                            #SBATCH --partition ${job_partition} 
                            #SBATCH --parsable
                            #SBATCH --output=std.out 
                            #SBATCH --error=std.err
                            #
                            # POSTFIX END
                            #
                        """.trimIndent().lines()

    println(headerSuffix)

    //find first nonwhitespace non comment line
    var headerEnd: Int = 0
        run loop@ {
            fileBody.forEachIndexed { idx, line -> 
                //println(line)
                    if( !line.trim().startsWith("#") ) { 
                        headerEnd = idx
                        return@loop
                    }
            }
        }

    // append lines starting with headerEnd
    fileBody.addAll(headerEnd, headerSuffix)

    println(fileBody)
    
    // append shebang
    return fileBody.joinToString(prefix="#!/usr/bin/bash \n", separator="\n", postfix="\n#EOF\n")

}





class SlurmPlugin : ComputePlugin {

fun PluginContext.getStatus(id: String) : Status {

    var imStatus: Status = Status( id, UcloudState.IN_QUEUE, "PENDING", "Job is pending")

    runBlocking {

        val ipcClient = ipcClient ?: error("No ipc client")
        val slurmJob: SlurmJob = ipcClient.sendRequestBlocking( JsonRpcRequest( "get.job", defaultMapper.encodeToJsonElement( SlurmJob(id, "someid", "normal", 1 ) ) as JsonObject ) ).orThrow<SlurmJob>()


        val (code, stdout, stderr) = CmdBuilder("/usr/bin/squeue")
                                    .addArg("--partition",  slurmJob.partition)
                                    .addArg("--jobs",       slurmJob.slurmId)
                                    .addArg("--noheader")
                                    .addArg("--format", "%T")
                                    .addEnv("SLURM_CONF",  "/etc/slurm/slurm.conf")
                                    .execute()
        
        //if job information is removed due to MinJobAge then squeue will throw slurm_load_jobs error: Invalid job id specified . Need to also check sacct in this case
        val sacct = if(code != 0) CmdBuilder("/usr/bin/sacct")
                            .addArg("--partition",  slurmJob.partition)
                            .addArg("--jobs",       slurmJob.slurmId)
                            .addArg("--allusers")
                            .addArg("--format", "jobid,state,exitcode")
                            .addArg("--noheader")
                            .addArg("--completion")
                            .addArg("--parsable2")
                            .addEnv("SLURM_CONF",  "/etc/slurm/slurm.conf")
                            .execute() 
                    else  ProcessResultText(1, "", "")


    

        val slurmStatus = if (code == 0) stdout.trim() else sacct.stdout.lines().get(0).split("|").get(1)
        
        imStatus =   when (slurmStatus) {
                                "PENDING", "CONFIGURING", "RESV_DEL_HOLD", "REQUEUE_FED", "SUSPENDED"       -> Status( id, JobState.IN_QUEUE, slurmStatus, "Job is queued" )
                                "REQUEUE_HOLD"                                                              -> Status( id, JobState.IN_QUEUE, slurmStatus, "Job is held for requeue" )  
                                "REQUEUED"                                                                  -> Status( id, JobState.IN_QUEUE, slurmStatus, "Job is requeued" ) 
                                "RESIZING"                                                                  -> Status( id, JobState.IN_QUEUE, slurmStatus, "Job is resizing" ) 
                                "RUNNING", "COMPLETING", "SIGNALING", "SPECIAL_EXIT", "STAGE_OUT"           -> Status( id, JobState.RUNNING, slurmStatus, "Job is running" )
                                "STOPPED"                                                                   -> Status( id, JobState.RUNNING, slurmStatus, "Job is stopped" )
                                "COMPLETED", "CANCELLED", "FAILED"                                          -> Status( id, JobState.SUCCESS, slurmStatus, "Job is success" )
                                "OUT_OF_MEMORY"                                                             -> Status( id, JobState.SUCCESS, slurmStatus, "Out of memory" )
                                "BOOT_FAIL", "NODE_FAIL"                                                    -> Status( id, JobState.FAILURE, slurmStatus, "Job is failed" )
                                "REVOKED"                                                                   -> Status( id, JobState.FAILURE, slurmStatus, "Job is revoked" )
                                "PREEMPTED"                                                                 -> Status( id, JobState.FAILURE, slurmStatus, "Preempted" )
                                "DEADLINE", "TIMEOUT"                                                       -> Status( id, JobState.EXPIRED, slurmStatus, "Job is expired" )
                                else -> throw RPCException("Unknown Slurm Job Status", HttpStatusCode.BadRequest)
                            }
        
    }

     return imStatus

}

    override fun PluginContext.retrieveSupport(): ComputeSupport {
        return ComputeSupport(
            ComputeSupport.Docker(
                enabled = true,
                logs = true,
                timeExtension = true,
                terminal = true,
                utilization = true
            )
        )
    }


    override fun PluginContext.initialize() {
        val log = Log("SlurmComputePlugin")

        ipcServer?.addHandler(
            IpcHandler("add.job") { user, jsonRequest ->
                log.debug("Asked to add new job mapping!")
                val req = runCatching {
                    defaultMapper.decodeFromJsonElement<SlurmJob>(jsonRequest.params)
                }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                //println(req)

                (dbConnection ?: error("No DB connection available")).withTransaction { connection ->

                        connection.prepareStatement(
                                        """
                                            insert into job_mapping (local_id, ucloud_id, partition, status) values ( :local_id, :ucloud_id, :partition, :status )
                                        """
                        ).useAndInvokeAndDiscard {
                                            bindString("ucloud_id", req.ucloudId)
                                            bindString("local_id", req.slurmId )
                                            bindString("normal", req.partition )
                                            bindInt("status", req.status )
                        }
                }

                JsonObject(emptyMap())
            }
        )




        ipcServer?.addHandler(
            IpcHandler("get.job") { user, jsonRequest ->
                log.debug("Asked to get job!")
                val req = runCatching {
                    defaultMapper.decodeFromJsonElement<SlurmJob>(jsonRequest.params)
                }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                var slurmJob:SlurmJob? = null

                (dbConnection ?: error("No DB connection available")).withTransaction { connection ->

                        connection.prepareStatement(
                                        """
                                            select * 
                                            from job_mapping 
                                            where ucloud_id = :ucloud_id
                                        """
                        ).useAndInvoke (
                            prepare = { bindString("ucloud_id", req.ucloudId) },
                            readRow = { slurmJob = SlurmJob(it.getString(0)!!, it.getString(1)!! , it.getString(2)!!, it.getInt(3)!! ) }
                        )
                }

                //println(" DATABASE RESULT $slurmJob")

                defaultMapper.encodeToJsonElement(slurmJob) as JsonObject 
            }
        )


        // ipcServer?.addHandler(
        //     IpcHandler("get.jobs.active") { user, jsonRequest ->
        //         log.debug("Asked to get active jobs!")
        //         val req = runCatching {
        //             defaultMapper.decodeFromJsonElement<SlurmJob>(jsonRequest.params)
        //         }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

        //         var slurmJob:SlurmJob? = null
        //         var stringResult:String? = null

        //         (dbConnection ?: error("No DB connection available")).withTransaction { connection ->

        //                 connection.prepareStatement(
        //                                 """
        //                                     select * 
        //                                     from job_mapping 
        //                                     where status = 1
        //                                 """
        //                 ).useAndInvoke (
        //                     readRow = { slurmJob = SlurmJob(it.getString(0)!!, it.getString(1)!! , it.getString(2)!!, it.getInt(3)!! ) }
        //                 )
        //         }

        //         //println(" DATABASE RESULT $slurmJob")

        //         defaultMapper.encodeToJsonElement(slurmJob) as JsonObject 
        //     }
        // )


    }


    override fun PluginContext.create(job: Job) {
        val client = rpcClient ?: error("No client")

        mkdir("/data/${job.id}", "0770".toUInt(8) )
        val job_content = job.specification?.parameters?.getOrElse("file_content") { throw Exception("no file_content") } as Text
        val sbatch_content = manageHeader(job, config)


        
        NativeFile.open(path="/data/${job.id}/job.req", readOnly = false).writeText(job_content.value)
        NativeFile.open(path="/data/${job.id}/job.sbatch", readOnly = false).writeText(sbatch_content)

        runBlocking {

        val (code, stdout, stderr) = CmdBuilder("/usr/bin/sbatch").addArg("/data/${job.id}/job.sbatch").addEnv("SLURM_CONF", "/etc/slurm/slurm.conf").execute()
        if ( code != 0 ) throw RPCException("Unhandled exception when creating job: $stderr", HttpStatusCode.BadRequest)

        var slurmId = stdout


        val ipcClient = ipcClient ?: error("No ipc client")

        val job_partition = config.plugins?.compute?.plugins?.first{ it.id == "slurm"  }?.configuration?.partition.toString()
        
        ipcClient.sendRequestBlocking( JsonRpcRequest( "add.job", defaultMapper.encodeToJsonElement(   SlurmJob(job.id, slurmId.trim(), job_partition, 1 )   ) as JsonObject ) ).orThrow<Unit>()

        sleep(2)

        JobsControl.update.call(
            bulkRequestOf(
                JobsControlUpdateRequestItem(
                    job.id,
                    JobState.IN_QUEUE,
                    "The job has been queued"
                )
            ), client
        ).orThrow()    

        sleep(5)

        JobsControl.update.call(
            bulkRequestOf(
                JobsControlUpdateRequestItem(
                    job.id,
                    JobState.RUNNING,
                    "The job is RUNNING"
                )
            ), client
        ).orThrow()    


        }

    }

    override fun PluginContext.delete(job: Job) {
        val client = rpcClient ?: error("No client")

        println("delete job")

        val job_partition = config.plugins?.compute?.plugins?.first{ it.id == "slurm"  }?.configuration?.partition


        val ipcClient = ipcClient ?: error("No ipc client")
        val slurmJob: SlurmJob = ipcClient.sendRequestBlocking( JsonRpcRequest( "get.job", defaultMapper.encodeToJsonElement( SlurmJob(job.id, "someid", "normal", 1 ) ) as JsonObject ) ).orThrow<SlurmJob>()


        // Sends SIGKILL or custom with -s INTEGER
        val (code, stdout, stderr) = CmdBuilder("/usr/bin/scancel")
                .addArg("--partition", job_partition)    // ${job_partition}
                .addArg("--full")
                .addArg("${slurmJob.slurmId}")
                .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf")
                .execute()

        sleep(2)
        runBlocking {
            JobsControl.update.call(
                bulkRequestOf(
                    JobsControlUpdateRequestItem(
                        job.id,
                        JobState.EXPIRED,
                        "The job is being cancelled!"
                    )
                ),
                client
            ).orThrow()
        }

        //TODO: mark job inactive in db
    }

    override fun PluginContext.retrieveClusterUtilization(): JobsProviderUtilizationResponse {
            // squeue --format '%A|%m|%C|%T' --noheader --states running,pending --noconvert
            // 26|50M|1|PENDING
            // 27|50M|1|PENDING

            //get pending cpu/mem jobs
            val (_, jobs, _) = CmdBuilder("/usr/bin/squeue")
                                    .addArg("--format","%A|%m|%C|%T")
                                    .addArg("--noheader")
                                    .addArg("--noconvert")
                                    .addArg("--states", "running,pending")
                                    .addEnv("SLURM_CONF",  "/etc/slurm/slurm.conf")
                                    .execute()

            val mList = jobs.lines().map{
                it.trimIndent()
                it.trim()
                it.split("|")
            }.toList()

            var usedCpu = 0;
            var usedMem = 0;
            var pendingJobs = 0;
            var runningJobs = 0;

            mList.forEach{ line -> 

                    if(  line[3].equals("PENDING") ) {
                       pendingJobs++
                    }

                    if(  line[3].equals("RUNNING")  ) {
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
            val (_, nodes, _) = CmdBuilder("/usr/bin/sinfo")
                                    .addArg("--format","%n|%c|%m")
                                    .addArg("--noheader")
                                    .addArg("--noconvert")
                                    .addEnv("SLURM_CONF",  "/etc/slurm/slurm.conf")
                                    .execute()

            val nList = nodes.lines().map{
                it.trimIndent()
                it.trim()
                it.split("|")
            }.toList()

            var clusterCpu = 0;
            var clusterMem = 0;

            nList.forEach{ line -> 
                        clusterCpu = clusterCpu + line[1].toInt()
                        clusterMem = clusterMem + line[2].replace("M", "").toInt()
            }

            //println("$clusterCpu $clusterMem")

        return JobsProviderUtilizationResponse(   CpuAndMemory(clusterCpu.toDouble(), clusterMem.toLong()), CpuAndMemory(usedCpu.toDouble(), usedMem.toLong()), QueueStatus(runningJobs, pendingJobs)  ) 
    }



    override fun PluginContext.extend(request: JobsProviderExtendRequestItem) {
        val client = rpcClient ?: error("No client")
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override fun PluginContext.suspendJob(request: JobsProviderSuspendRequestItem) {
        println("Suspending job!")
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override fun PluginContext.runMonitoringLoop() { 

            val client = rpcClient ?: error("No client")

            Log("RunMonitoringLoop")

                val terminalStates = listOf("COMPLETED", "CANCELLED", "FAILED", "OUT_OF_MEMORY","BOOT_FAIL", "NODE_FAIL", "PREEMPTED", "REVOKED", "DEADLINE", "TIMEOUT"  )


                while(true) {
                    println("RunMonitoringLoop")
                    sleep(5)

                    var jobs:MutableList<SlurmJob> = mutableListOf()
                    
                    (dbConnection ?: error("No DB connection available")).withTransaction { connection ->

                            connection.prepareStatement(
                                                        """
                                                            select * 
                                                            from job_mapping 
                                                            where status = 1
                                                        """
                                        ).useAndInvoke(
                                            readRow = { 
                                                jobs.add(SlurmJob(it.getString(0)!!, it.getString(1)!! , it.getString(2)!!, it.getInt(3)!! )  )
                                            }
                                        )
                    }


                    // --ids 7.batch,8.batch ...
                    var ids = "2.batch" // jobs.fold( "", { acc, item ->   StringBuilder().append(acc).append(item.slurmId).append(".batch,").toString() }  )


                    //println(ids)

                    val ( _ , stdout , _ ) = CmdBuilder("/usr/bin/sacct")
                                        .addArg("--jobs",      ids )
                                        .addArg("--allusers")
                                        .addArg("--format", "jobid,state,exitcode,start,end")
                                        .addArg("--noheader")
                                        .addArg("--parsable2")
                                        .addEnv("SLURM_CONF",  "/etc/slurm/slurm.conf")
                                        .execute()
                    println(stdout.lines() )
                    var slurmJobs = if ( !stdout.trim().isEmpty() ) stdout.lines() else continue



                    slurmJobs.forEach{ job ->
                        val id = job.toString().split("|").get(0)
                        val state = job.toString().split("|").get(1)
                        val start =  Instant.parse( "2021-09-30T14:29:11Z" )
                        val end   =  Instant.parse( "2021-09-30T14:34:22Z" )
                        val lastTs = Clock.System.now()

                        if ( state in terminalStates ) {
                            val timeRunning: Duration = end - start
                            //TODO: charge time

                             runBlocking {
                                    JobsControl.chargeCredits.call(
                                        bulkRequestOf( JobsControlChargeCreditsRequestItem (id, lastTs.toString(), SimpleDuration( 0, 5, 15 ) )),
                                        client
                                    ).orThrow()
                            }


                            //TODO: update table job_mapping.status = 0 where slurmId in ( list ) 

                        }
        
                    }       


                

                }



    }



    override fun ComputePlugin.FollowLogsContext.followLogs(job: Job) {
            val client = rpcClient ?: error("No client")

            sleep(2)
            println("follow logs")

            
            val stdOut = NativeFile.open(path="/data/${job.id}/std.out", readOnly = true)
            val stdErr = NativeFile.open(path="/data/${job.id}/std.err", readOnly = true)

    thisLoop@ while ( isActive() ) {
                val mState:Status = getStatus(job.id)
                when ( mState.ucloudStatus ) {

                        JobState.RUNNING -> {

                            val line = stdOut.readText(autoClose = false)
                            if ( !line.isNullOrEmpty() ) emitStdout(0, "[${ Clock.System.now() }] OUT: ${ line.trim() } \n")

                            val err = stdOut.readText(autoClose = false)
                            if ( !err.isNullOrEmpty() ) emitStderr(0, "[${ Clock.System.now() }] ERR: ${ err.trim() } \n")

                        }

                        else -> {   
                                        runBlocking {
                                            JobsControl.update.call(
                                                bulkRequestOf( JobsControlUpdateRequestItem(mState.id, mState.ucloudStatus, mState.message)),
                                                client
                                            ).orThrow()
                                        }

                                        break@thisLoop
                        }
                }

                sleep(5)
            }


    }

}










