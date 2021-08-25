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
            ),
            ComputeSupport.VirtualMachine(
                enabled = false,
                logs = true,
                suspension = true
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

                println(req)

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
 
        val job_content = job.specification?.parameters?.getOrElse("file_content") { throw Exception("no file_content") } as Text
        val job_timelimit = "${job.specification?.timeAllocation?.hours}:${job.specification?.timeAllocation?.minutes}:${job.specification?.timeAllocation?.seconds}" 
        val request_product = job.specification?.product as ProductReference

        val product = compute!!.firstOrNull{ it.id == request_product.id } as ProductReferenceWithoutProvider
        val job_partition = "normal" //product!!.id 
        val product_cpu = product!!.cpu
        val product_mem = product!!.mem
        val product_gpu = product!!.gpu


        mkdir("/data/${job.id}", "0770".toUInt(8) )
        NativeFile.open(path="/data/${job.id}/job.sbatch", readOnly = false).writeText(job_content.value)

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


        val mString = popen("SLURM_CONF=/etc/slurm/slurm.conf /usr/bin/sbatch --chdir /data/${job.id} --cpus-per-task ${product_cpu} --mem ${product_mem} --gpus-per-node ${product_gpu} --time ${job_timelimit} --job-name ${job.id} --partition ${job_partition} --parsable --output=std.out --error=std.err  job.sbatch", "r")

        val slurmId = buildString {
            val buffer = ByteArray(4096)
            while (true) {
                val input = fgets(buffer.refTo(0), buffer.size, mString) ?: break
                append(input.toKString())
            }
        }
        pclose(mString)



        val ipcClient = ipcClient ?: error("No ipc client")
        ipcClient.sendRequestBlocking( JsonRpcRequest( "add.job", defaultMapper.encodeToJsonElement(SlurmJob(job.id, slurmId.trim(), job_partition )) as JsonObject ) ).orThrow<Unit>()
        sleep(2)

        JobsControl.update.call(
            bulkRequestOf(
                JobsControlUpdateRequestItem(
                    job.id,
                    JobState.IN_QUEUE,
                    "The job has been queued!"
                )
            ), client
        ).orThrow()    


        JobsControl.update.call(
            bulkRequestOf(
                JobsControlUpdateRequestItem(
                    job.id,
                    JobState.RUNNING,
                    "We are now running!"
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

        // Sends SIGKILL or custom with -s INTEGER
        val (code, stdout, stderr) = CmdBuilder("/usr/bin/scancel")
                .addArg("--partition", "normal")    // ${job_partition}
                .addArg("--full")
                .addArg("--name", "${job.id}")
                .addEnv("SLURM_CONF", "/etc/slurm/slurm.conf")
                .execute()

        println("CANCEL $code - $stdout - $stderr")

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

            sleep(2)
            println("follow logs")
            
            val stdOut = NativeFile.open(path="/data/${job.id}/std.out", readOnly = true)
            val stdErr = NativeFile.open(path="/data/${job.id}/std.err", readOnly = true)

    thisLoop@ while ( isActive() ) {
                when( getStatus(job.id) ) {

                    JobState.RUNNING -> {

                        val line = stdOut.readText(autoClose = false)
                        if ( !line.isNullOrEmpty() ) emitStdout(0, "STDOUT: ${ line } \n")

                        val err = stdOut.readText(autoClose = false)
                        if ( !err.isNullOrEmpty() ) emitStdout(0, "STDERR: ${ line } \n")

                    }

                    else ->  break@thisLoop


                }

                sleep(5)
            }


    }






}
