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
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.runBlocking

class SlurmJobTracker(
    private val slurmPollAgent: SlurmPollAgent,
    private val jobFileService: JobFileService,
    private val sshConnectionPool: SSHConnectionPool,
    private val cloud: AuthenticatedCloud
) {
    private val listener: SlurmEventListener = {
        runBlocking {
            val systemId = try {
                resolveSlurmIdToSystemId(it.jobId)
            } catch (ex: Exception) {
                log.warn("Received slurm event for job we don't know about!")
                log.warn("Event was $it")
                return@runBlocking
            }

            try {
                processEvent(systemId, it)
            } catch (ex: Exception) {
                handleException(ex, systemId, it)
            }
        }
    }

    fun init() {
        slurmPollAgent.addListener(listener)
    }

    fun stop() {
        slurmPollAgent.removeListener(listener)
    }

    private suspend fun processEvent(systemId: String, event: SlurmEvent) {
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

    private suspend fun handleException(exception: Exception, systemId: String, event: SlurmEvent) {
        val message =
            if (exception is RPCException && exception.httpStatusCode != HttpStatusCode.InternalServerError) {
                exception.why
            } else {
                null
            }

        if (exception !is RPCException) {
            log.warn("Unexpected exception: $event [$systemId]")
            log.warn(exception.stackTraceToString())
        } else {
            log.debug("Expected exception: $event [$systemId]")
            log.debug(exception.stackTraceToString())
        }

        val stateResult = ComputationCallbackDescriptions.requestStateChange.call(
            StateChangeRequest(
                systemId,
                JobState.FAILURE,
                message ?: "Internal error"
            ),
            cloud
        )

        if (stateResult is RESTResponse.Err) {
            log.warn("Could not notify orchestrator about failure $event [$systemId]")
            log.warn(stateResult.response.toString())
        }
    }

    private fun resolveSlurmIdToSystemId(slurmId: Long): String = TODO()

    companion object : Loggable {
        override val log = logger()
    }
}
