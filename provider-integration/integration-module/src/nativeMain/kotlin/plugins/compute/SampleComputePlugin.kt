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
    val slurmId: String,
    //val state: String?,
    //val exitCode: String?
)


val compute : List<ProductReferenceWithoutProvider>?  by lazy {

        val plugins = Json.decodeFromString<IMConfiguration.Plugins>(
            NativeFile.open("/etc/ucloud/plugins.json", readOnly = true).readText()
        ) as IMConfiguration.Plugins

        val compute_products = plugins?.compute as ProductBasedConfiguration
        compute_products?.products

}


class SampleComputePlugin : ComputePlugin {

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
                                            insert into job_mapping (local_id, ucloud_id) values ( :local_id, :ucloud_id )
                                        """
                        ).useAndInvokeAndDiscard {
                                            bindString("ucloud_id", req.ucloudId)
                                            bindString("local_id", req.slurmId )
                        }
                }

                JsonObject(emptyMap())
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

        val stdout = buildString {
            val buffer = ByteArray(4096)
            while (true) {
                val input = fgets(buffer.refTo(0), buffer.size, mString) ?: break
                append(input.toKString())
            }
        }
        val status = pclose(mString)



        val ipcClient = ipcClient ?: error("No ipc client")
        ipcClient.sendRequestBlocking( JsonRpcRequest( "add.job", defaultMapper.encodeToJsonElement(SlurmJob(job.id, stdout)) as JsonObject ) ).orThrow<Unit>()
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
            var count = 0
            sleep(2)
            println("follow logs")
            while (isActive() ) {
                emitStdout(0, "Hello, World :: ${count++}!\n")
                sleep(1)
            }
    }






}
