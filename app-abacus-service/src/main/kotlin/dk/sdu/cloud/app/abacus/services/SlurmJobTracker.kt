package dk.sdu.cloud.app.abacus.services

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.abacus.services.ssh.slurmJobInfo
import dk.sdu.cloud.app.abacus.services.ssh.use
import dk.sdu.cloud.app.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.api.JobCompletedRequest
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.StateChangeRequest
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.orThrow
import kotlinx.coroutines.experimental.runBlocking

class SlurmJobTracker(
    private val slurmPollAgent: SlurmPollAgent,
    private val jobFileService: JobFileService,
    private val sshConnectionPool: SSHConnectionPool,
    private val cloud: AuthenticatedCloud
) {
    private val listener: SlurmEventListener = {
        runBlocking {
            try {
                processEvent(it)
            } catch (ex: Exception) {
                handleException(ex, it)
            }
        }
    }

    fun init() {
        slurmPollAgent.addListener(listener)
    }

    fun stop() {
        slurmPollAgent.removeListener(listener)
    }

    private suspend fun processEvent(event: SlurmEvent) {
        val systemId = resolveSlurmIdToSystemId(event.jobId)

        when (event) {
            is SlurmEventRunning -> {
                ComputationCallbackDescriptions.requestStateChange.call(
                    StateChangeRequest(systemId, JobState.RUNNING, "Job is now running."),
                    cloud
                ).orThrow()
            }

            is SlurmEventEnded -> {
                ComputationCallbackDescriptions.requestStateChange.call(
                    StateChangeRequest(
                        systemId,
                        JobState.TRANSFER_SUCCESS,
                        "Job has completed. We are now transferring your files back to SDUCloud."
                    ),
                    cloud
                ).orThrow()

                val verifiedJob = ComputationCallbackDescriptions.lookup.call(
                    FindByStringId(systemId),
                    cloud
                ).orThrow()

                jobFileService.transferForJob(verifiedJob)
                sendComplete(systemId, event.jobId, true)
            }

            is SlurmEventFailed, is SlurmEventTimeout -> {
                sendComplete(systemId, event.jobId, false)
            }
        }
    }

    private suspend fun sendComplete(systemId: String, slurmId: Long, success: Boolean) {
        ComputationCallbackDescriptions.completed.call(
            JobCompletedRequest(systemId, computeUsage(slurmId), success),
            cloud
        ).orThrow()
    }

    private fun computeUsage(slurmId: Long): SimpleDuration {
        return sshConnectionPool.use { slurmJobInfo(slurmId) }
    }

    private fun handleException(exception: Exception, event: SlurmEvent) {
        // TODO The main service is not aware of these exceptions automatically. We must notify them that we are failing.
        // TODO The main service is not aware of these exceptions automatically. We must notify them that we are failing.
        // TODO The main service is not aware of these exceptions automatically. We must notify them that we are failing.
        // TODO The main service is not aware of these exceptions automatically. We must notify them that we are failing.
        // TODO The main service is not aware of these exceptions automatically. We must notify them that we are failing.
        // TODO The main service is not aware of these exceptions automatically. We must notify them that we are failing.
    }

    private fun resolveSlurmIdToSystemId(slurmId: Long): String = TODO()
}
