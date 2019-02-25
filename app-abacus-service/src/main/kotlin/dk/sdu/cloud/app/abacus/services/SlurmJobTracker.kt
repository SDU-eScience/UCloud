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
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SlurmJobTracker<DBSession>(
    private val jobFileService: JobFileService,
    private val sshConnectionPool: SSHConnectionPool,
    private val cloud: AuthenticatedClient,
    private val db: DBSessionFactory<DBSession>,
    private val jobDao: JobDao<DBSession>
) {
    val listener: SlurmEventListener = {
        @Suppress("TooGenericExceptionCaught")
        runBlocking {
            GlobalScope.launch {
                val systemId = try {
                    resolveSlurmIdToSystemId(it.jobId)
                } catch (ex: Exception) {
                    log.warn("Received slurm event for job we don't know about!")
                    log.warn("Event was $it")
                    return@launch
                }

                try {
                    processEvent(systemId, it)
                } catch (ex: Exception) {
                    handleException(ex, systemId, it)
                }
            }.join()
        }
    }

    private suspend fun processEvent(systemId: String, event: SlurmEvent) {
        when (event) {
            is SlurmEventRunning -> {
                val job = ComputationCallbackDescriptions.lookup.call(
                    FindByStringId(systemId),
                    cloud
                ).orThrow()

                if (job.currentState in setOf(JobState.PREPARED, JobState.VALIDATED, JobState.SCHEDULED)) {
                    ComputationCallbackDescriptions.requestStateChange.call(
                        StateChangeRequest(systemId, JobState.RUNNING, "Job is now running."),
                        cloud
                    ).orThrow()
                } else {
                    log.info("Ignoring event: $event. We are already in state ${job.currentState}.")
                }
            }

            is SlurmEventEnded, is SlurmEventFailed, is SlurmEventTimeout -> {
                val success = event is SlurmEventEnded
                val completedText =
                    if (success) "Job has completed. We are now transferring your files back to SDUCloud."
                    else "Job failed. We are now transferring back files to SDUCloud."

                ComputationCallbackDescriptions.requestStateChange.call(
                    StateChangeRequest(
                        systemId,
                        JobState.TRANSFER_SUCCESS,
                        completedText
                    ),
                    cloud
                ).orThrow()

                val verifiedJob = ComputationCallbackDescriptions.lookup.call(
                    FindByStringId(systemId),
                    cloud
                ).orThrow()

                jobFileService.transferForJob(verifiedJob)
                sendComplete(systemId, event.jobId, success)
            }
        }
    }

    private suspend fun sendComplete(systemId: String, slurmId: Long, success: Boolean) {
        ComputationCallbackDescriptions.completed.call(
            JobCompletedRequest(systemId, computeUsage(slurmId), success),
            cloud
        ).orThrow()
    }

    private suspend fun computeUsage(slurmId: Long): SimpleDuration {
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

        if (stateResult is IngoingCallResponse.Error) {
            log.warn("Could not notify orchestrator about failure $event [$systemId]")
            log.warn(stateResult.toString())
        }
    }

    private fun resolveSlurmIdToSystemId(slurmId: Long): String =
        db.withTransaction { jobDao.resolveSystemId(it, slurmId) }
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

    companion object : Loggable {
        override val log = logger()
    }
}
