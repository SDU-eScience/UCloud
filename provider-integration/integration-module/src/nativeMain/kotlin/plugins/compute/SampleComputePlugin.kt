package dk.sdu.cloud.plugins.compute

import dk.sdu.cloud.app.orchestrator.api.*

import dk.sdu.cloud.app.store.api.AppParameterValue.Text
import dk.sdu.cloud.accounting.api.ProductReference

import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.plugins.PluginContext
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

import kotlinx.coroutines.isActive


@Serializable
data class SlurmJob(
    val ucloudId: String,
    val slurmId: String, // TODO: is this string or integer? check with Martin
    //val cpu: In,
    //val mem: String,
    //val gpu: String,
    val partition: String
)


val compute : List<ProductReferenceWithoutProvider>?  by lazy {

        val plugins = Json.decodeFromString<IMConfiguration.Plugins>(
            NativeFile.open("/etc/ucloud/plugins.json", readOnly = true).readText()
        ) as IMConfiguration.Plugins

        val compute_products = plugins?.compute as ProductBasedConfiguration
        compute_products?.products

}


fun manageHeader(job:Job):String { 
 
        val job_content = job.specification?.parameters?.getOrElse("file_content") { throw Exception("no file_content") } as Text
        val job_timelimit = "${job.specification?.timeAllocation?.hours}:${job.specification?.timeAllocation?.minutes}:${job.specification?.timeAllocation?.seconds}" 
        val request_product = job.specification?.product as ProductReference

        val product = compute!!.firstOrNull{ it.id == request_product.id } as ProductReferenceWithoutProvider
        val job_partition = "normal" //product!!.id 
        val job_nodes = job.specification!!.replicas
        val product_cpu = product!!.cpu
        val product_mem = product!!.mem
        val product_gpu = product!!.gpu

    //sbatch will stop processing further #SBATCH directives once the first non-comment non-whitespace line has been reached in the script.
    // remove whitespaces
    var fileBody = job_content.value.lines().map{it.trim()}.toMutableList()

    //TODO: add --hold once interactive job is running 
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

    // append header lines starting with headerEnd
    fileBody.addAll(headerEnd, headerSuffix)
    
    // append shebang
    return fileBody.joinToString(prefix="#!/usr/bin/bash \n", separator="\n", postfix="\n#EOF\n")

}


class SampleComputePlugin : ComputePlugin {


fun PluginContext.getStatus(id: String) : JobState {

    var ucloudStatus: JobState = JobState.IN_QUEUE

    runBlocking {

        val ipcClient = ipcClient ?: error("No ipc client")
        val slurmJob: SlurmJob = ipcClient.sendRequestBlocking( JsonRpcRequest( "get.job", defaultMapper.encodeToJsonElement( SlurmJob(id, "someid", "somepartition" ) ) as JsonObject ) ).orThrow<SlurmJob>()

        val mString = popen("SLURM_CONF=/etc/slurm/slurm.conf /usr/bin/sacct --partition ${slurmJob.partition}  --jobs ${slurmJob.slurmId} --allusers --format jobid,state,exitcode --noheader --parsable2", "r")

        val stdout = buildString {
            val buffer = ByteArray(4096)
            while (true) {
                val input = fgets(buffer.refTo(0), buffer.size, mString) ?: break
                append(input.toKString())
            }
        }
        pclose(mString)

        val slurmStatus = stdout.lines().get(0).split("|").get(1)
        ucloudStatus =   when (slurmStatus) {
                                "PENDING", "CONFIGURING", "RESV_DEL_HOLD", "REQUEUE_FED", "REQUEUE_HOLD", "REQUEUED", "RESIZING", "SUSPENDED"   -> JobState.IN_QUEUE
                                "RUNNING", "COMPLETING", "SIGNALING", "SPECIAL_EXIT", "STAGE_OUT", "STOPPED"                                    -> JobState.RUNNING
                                "COMPLETED", "CANCELLED", "FAILED", "OUT_OF_MEMORY"                                                             -> JobState.SUCCESS
                                "BOOT_FAIL", "NODE_FAIL", "PREEMPTED", "REVOKED"                                                                -> JobState.FAILURE
                                "DEADLINE", "TIMEOUT"                                                                                           -> JobState.EXPIRED
                                else -> throw RPCException("Unknown Slurm Job Status", HttpStatusCode.BadRequest)
                            }
        
    }

     return ucloudStatus

}

    override fun PluginContext.retrieveSupport(): ComputeSupport {
        return ComputeSupport(
            ComputeSupport.Docker(
                enabled = true,
                logs = true,
                timeExtension = true,
                terminal = true
            )
        )
    }


    override fun PluginContext.initialize() {
        val log = Log("SampleComputePlugin")

        ipcServer?.addHandler(
            IpcHandler("add.job") { user, jsonRequest ->
                log.debug("Asked to add new job mapping!")
                val req = runCatching {
                    defaultMapper.decodeFromJsonElement<SlurmJob>(jsonRequest.params)
                }.getOrElse { throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

                println(req)



                (dbConnection ?: error("No DB connection available")).withTransaction { connection ->

                        connection.prepareStatement(
                                        """
                                            insert into job_mapping (local_id, ucloud_id, partition) values ( :local_id, :ucloud_id, :partition )
                                        """
                        ).useAndInvokeAndDiscard {
                                            bindString("ucloud_id", req.ucloudId)
                                            bindString("local_id", req.slurmId )
                                            bindString("partition", req.partition )
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
                var stringResult:String? = null

                (dbConnection ?: error("No DB connection available")).withTransaction { connection ->

                        connection.prepareStatement(
                                        """
                                            select * 
                                            from job_mapping 
                                            where ucloud_id = :ucloud_id
                                        """
                        ).useAndInvoke (
                            prepare = { bindString("ucloud_id", req.ucloudId) },
                            readRow = { slurmJob = SlurmJob(it.getString(0)!!, it.getString(1)!! , it.getString(2)!! ) }
                        )
                }

                //println(" DATABASE RESULT $slurmJob")

                defaultMapper.encodeToJsonElement(slurmJob) as JsonObject 
            }
        )


    }


    override fun PluginContext.create(job: Job) {
        val client = rpcClient ?: error("No client")
 
        //val job_content = job.specification?.parameters?.getOrElse("file_content") { throw Exception("no file_content") } as Text
        // val job_timelimit = "${job.specification?.timeAllocation?.hours}:${job.specification?.timeAllocation?.minutes}:${job.specification?.timeAllocation?.seconds}" 
        // val request_product = job.specification?.product as ProductReference
        // val product = compute!!.firstOrNull{ it.id == request_product.id } as ProductReferenceWithoutProvider
        // val job_partition = "normal" //product!!.id 
        // val product_cpu = product!!.cpu
        // val product_mem = product!!.mem
        // val product_gpu = product!!.gpu

        mkdir("/data/${job.id}", "0770".toUInt(8) )
        val job_content = job.specification?.parameters?.getOrElse("file_content") { throw Exception("no file_content") } as Text
        val sbatch_content = manageHeader(job)

        
        NativeFile.open(path="/data/${job.id}/job.req", readOnly = false).writeText(job_content.value)
        NativeFile.open(path="/data/${job.id}/job.sbatch", readOnly = false).writeText(sbatch_content)

        runBlocking {

    //    val (code, stdout, stderr) = CmdBuilder("/usr/bin/sbatch")
    //                                 .addArg("--chdir", "/data/${job.id}")
    //                                 .addArg("--cpus-per-task", "${product_cpu}")
    //                                 .addArg("--mem", "${product_mem}")
    //                                 .addArg("--gpus-per-node", "${product_gpu}")
    //                                 .addArg("--time", "${job_timelimit}")
    //                                 .addArg("--job-name", "${job.id}")
    //                                 .addArg("--partition", "normal")    // ${job_partition}
    //                                 .addArg("--parsable")
    //                                 .addArg("--output", "std.out")
    //                                 .addArg("--error", "std.err")
    //                                 .addArg("job.sbatch")
    //                                 .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf")
    //                                 .execute()

    //     println("PLUGINDATA: $code  $stdout  $stderr")

        //TODO: solve formatting errors
        val mString = popen("SLURM_CONF=/etc/slurm/slurm.conf /usr/bin/sbatch /data/${job.id}/job.sbatch", "r")

        val slurmId = buildString {
            val buffer = ByteArray(4096)
            while (true) {
                val input = fgets(buffer.refTo(0), buffer.size, mString) ?: break
                append(input.toKString())
            }
        }
        pclose(mString)



        val ipcClient = ipcClient ?: error("No ipc client")
        val request_product = job.specification?.product as ProductReference
        val product = compute!!.firstOrNull{ it.id == request_product.id } as ProductReferenceWithoutProvider
        val job_partition = "normal" //product!!.id 
        ipcClient.sendRequestBlocking( JsonRpcRequest( "add.job", defaultMapper.encodeToJsonElement(SlurmJob(job.id, slurmId.trim(), job_partition )) as JsonObject ) ).orThrow<Unit>()
        sleep(2)

        //TODO: check status
        JobsControl.update.call(
            bulkRequestOf(
                JobsControlUpdateRequestItem(
                    job.id,
                    JobState.IN_QUEUE,
                    "The job has been queued. Log into url.dtu.dk and approve request."
                )
            ), client
        ).orThrow()    


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
        val request_product = job.specification?.product as ProductReference
        val product = compute!!.firstOrNull{ it.id == request_product.id } as ProductReferenceWithoutProvider
        val job_partition = "normal" //product!!.id 


        val ipcClient = ipcClient ?: error("No ipc client")
        val slurmJob: SlurmJob = ipcClient.sendRequestBlocking( JsonRpcRequest( "get.job", defaultMapper.encodeToJsonElement( SlurmJob(job.id, "someid", "somepartition" ) ) as JsonObject ) ).orThrow<SlurmJob>()


        // Sends SIGKILL or custom with -s INTEGER
        //DONE: replace --name with --job
        val (code, stdout, stderr) = CmdBuilder("/usr/bin/scancel")
                .addArg("--partition", "normal")    // ${job_partition}
                .addArg("--full")
                .addArg("${slurmJob.slurmId}")
                .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf")
                .execute()

        println("CANCEL $code - $stdout - $stderr")
        //check process status and change to SUCCESS
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
    }


    override fun PluginContext.extend(request: JobsProviderExtendRequestItem) {
        val client = rpcClient ?: error("No client")
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }

    override fun PluginContext.suspendJob(request: JobsProviderSuspendRequestItem) {
        println("Suspending job!")
        throw RPCException("Not supported", HttpStatusCode.BadRequest)
    }


    override fun ComputePlugin.FollowLogsContext.followLogs(job: Job) {
            val client = rpcClient ?: error("No client")

            sleep(2)
            println("follow logs")

            fun getTime():String{

                    val mString = popen("/usr/bin/date +\"%Y/%m/%d %H:%M:%S:%N\" ", "r")

                    val stdout = buildString {
                        val buffer = ByteArray(4096)
                        while (true) {
                            val input = fgets(buffer.refTo(0), buffer.size, mString) ?: break
                            append(input.toKString())
                        }
                    }
                    pclose(mString)

                    return stdout.replace("\r", "").replace("\n", "").trim()
            }

            
            val stdOut = NativeFile.open(path="/data/${job.id}/std.out", readOnly = true)
            val stdErr = NativeFile.open(path="/data/${job.id}/std.err", readOnly = true)

    thisLoop@ while ( isActive() ) {
                val mState = getStatus(job.id)
                when ( mState ) {

                        JobState.RUNNING -> {
                            val line = stdOut.readText(autoClose = false)
                            if ( !line.isNullOrEmpty() ) emitStdout(0, "[${ getTime() }] OUT: ${ line.trim() } \n")

                            val err = stdOut.readText(autoClose = false)
                            if ( !err.isNullOrEmpty() ) emitStdout(0, "[${ getTime() }] ERR: ${ err.trim() } \n")

                        }

                        else -> {  
                                        runBlocking {
                                            JobsControl.update.call(
                                                bulkRequestOf(
                                                    JobsControlUpdateRequestItem(
                                                        job.id,
                                                        mState,
                                                        "The job has finished!"
                                                    )
                                                ),
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
