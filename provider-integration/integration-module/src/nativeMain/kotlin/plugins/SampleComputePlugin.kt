package dk.sdu.cloud.plugins

import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import kotlinx.coroutines.runBlocking
import platform.posix.sleep

class SampleComputePlugin : ComputePlugin {
    override fun PluginContext.create(job: Job) {
        val client = rpcClient ?: error("No client")
        sleep(2)
        runBlocking {
            JobsControl.update.call(
                bulkRequestOf(
                    JobsControlUpdateRequestItem(
                        job.id,
                        JobState.RUNNING,
                        "We are now running!"
                    )
                ),
                client
            ).orThrow()
        }
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
        val client = rpcClient ?: error("No client")
        println("Suspending job!")
    }

    override fun ComputePlugin.FollowLogsContext.followLogs(job: Job) {
        var count = 0
        while (isActive()) {
            emitStdout(0, "Hello, World ${count++}!\n")
            sleep(1)
        }
    }
}
