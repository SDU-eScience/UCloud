package dk.sdu.cloud.plugins.compute

import dk.sdu.cloud.app.orchestrator.api.*

import dk.sdu.cloud.app.store.api.AppParameterValue.Text

import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.plugins.ComputePlugin
import dk.sdu.cloud.plugins.PluginContext
import kotlinx.coroutines.runBlocking
import platform.posix.*
import kotlinx.cinterop.*
import dk.sdu.cloud.utils.*

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

    // using the following sample file
    // pushd /data; echo -e '#!/bin/bash \n#SBATCH --time=00:01:00 \n#SBATCH --nodes=2 \n#SBATCH --ntasks-per-node=1 \necho "hello world" \n' > job.sbatch

    override fun PluginContext.create(job: Job) {
        val client = rpcClient ?: error("No client")


        val content = job.specification?.parameters!!.getOrElse("file_content") {"file_content is empty"} as Text
        val timeLimit = "${job.specification?.timeAllocation?.hours}:${job.specification?.timeAllocation?.minutes}:${job.specification?.timeAllocation?.seconds}"
        val mPartition = "normal"

        

        createFile("/data/job.sbatch", content.value)

        //--exclusive? ask martin later
        //popen("sleep 10s; SLURM_CONF=/etc/slurm/slurm.conf /usr/bin/sbatch --chdir=/data --time=${timeLimit} --partition={mPartition} job.sbatch", "r")


        startProcessAndCollectToString(
            listOf(
                "/usr/bin/sbatch",
                "--chdir",
                "/data",
                // "--time",
                // timeLimit,
                // "--partition",
                // mPartition,
                "job.sbatch"
            )
        )







        //TODO: use sequel to map ucloud job id to slurm job id

        // runBlocking {
        //     JobsControl.update.call(
        //         bulkRequestOf(
        //             JobsControlUpdateRequestItem(
        //                 job.id,
        //                 JobState.RUNNING,
        //                 "We are now running!"
        //             )
        //         ),
        //         client
        //     ).orThrow()    

        // }


        // startProcessAndCollectToString(
        //     listOf(
        //         "/usr/bin/sleep",
        //         "20s"
        //          //"'#!/bin/bash \n#SBATCH --time=00:01:00 \n#SBATCH --nodes=2 \n#SBATCH --ntasks-per-node=1 \necho hello_world \n' ",
        //         // "sbatch",
        //         // "job.sbatch"
        //     )
        // )


    }

    override fun PluginContext.delete(job: Job) {
        val client = rpcClient ?: error("No client")
        runBlocking {
            JobsControl.update.call(
                bulkRequestOf(
                    JobsControlUpdateRequestItem(
                        job.id,
                        JobState.SUCCESS,
                        "We are no longer running!"
                    )
                ),
                client
            ).orThrow()
        }

        val result: ProcessResultText = startProcessAndCollectToString(
                listOf(
                        "scancel",
                        job.id
                )
        )

    }

    override fun PluginContext.extend(request: JobsProviderExtendRequestItem) {
        val client = rpcClient ?: error("No client")
        runBlocking {
            JobsControl.update.call(
                bulkRequestOf(
                    JobsControlUpdateRequestItem(
                        request.job.id,
                        status = "We have extended your requestItem with ${request.requestedTime}"
                    )
                ),
                client
            ).orThrow()
        }
    }

    override fun PluginContext.suspendJob(request: JobsProviderSuspendRequestItem) {
        println("Suspending job!")

        //TODO: destructuring
        val result: ProcessResultText = startProcessAndCollectToString(
                listOf(
                        "scontrol",
                        "suspend",
                        request.id
                )
        )
        //TODO: error handling
        //if result.statusCode != 0 blabla
    }


    //check if slurm streams to a file

    override fun ComputePlugin.FollowLogsContext.followLogs(job: Job) {
            var count = 0
            while (isActive()) {
                emitStdout(0, "Hello, World :: ${count++}!\n")
                sleep(1)
            }
        }
}


// data class Cmd( val binary: String, val timeLimit: String?, val partition: String?, val chDir: String?, val file: String? ) {
//     val commandString:String = "sleep 10s; SLURM_CONF=/etc/slurm/slurm.conf ${binary} ${timeLimit} ${partition} ${chDir} ${file}"

// }

fun createFile(filePath: String, fileContent: String) {
    val file = fopen(filePath, "w+") ?: throw Error("IO Exception")
    try {
        fputs(fileContent, file)
    } finally {
        fclose(file)
    }
}