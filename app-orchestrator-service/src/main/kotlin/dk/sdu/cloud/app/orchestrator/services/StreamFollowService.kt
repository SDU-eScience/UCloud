package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.CancelWSStreamRequest
import dk.sdu.cloud.app.orchestrator.api.FollowStdStreamsRequest
import dk.sdu.cloud.app.orchestrator.api.FollowStdStreamsResponse
import dk.sdu.cloud.app.orchestrator.api.FollowWSRequest
import dk.sdu.cloud.app.orchestrator.api.FollowWSResponse
import dk.sdu.cloud.app.orchestrator.api.InternalFollowStdStreamsRequest
import dk.sdu.cloud.app.orchestrator.api.InternalFollowWSStreamRequest
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.subscribe
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StreamFollowService<DBSession>(
    private val jobFileService: JobFileService,
    private val serviceClient: AuthenticatedClient,
    private val serviceClientWS: AuthenticatedClient,
    private val computationBackendService: ComputationBackendService,
    private val db: DBSessionFactory<DBSession>,
    private val jobDao: JobDao<DBSession>
) {
    suspend fun followStreams(
        request: FollowStdStreamsRequest,
        requestedBy: String
    ): FollowStdStreamsResponse {
        val (job) = db.withTransaction { jobDao.find(it, request.jobId) }
        if (job.owner != requestedBy) throw RPCException("Not found", HttpStatusCode.NotFound)

        val backend = computationBackendService.getAndVerifyByName(job.backend, null)
        val internalResult = backend.follow.call(
            InternalFollowStdStreamsRequest(
                job,
                request.stdoutLineStart,
                request.stdoutMaxLines,
                request.stderrLineStart,
                request.stderrMaxLines
            ),
            serviceClient
        ).orThrow()

        return FollowStdStreamsResponse(
            internalResult.stdout,
            internalResult.stdoutNextLine,
            internalResult.stderr,
            internalResult.stderrNextLine,
            NameAndVersion(job.application.metadata.name, job.application.metadata.version),
            job.currentState,
            job.status,
            job.currentState.isFinal(),
            job.failedState,
            job.timeLeft,
            job.id,
            job.name,
            jobFileService.jobFolder(job),
            job.application.metadata
        )
    }

    suspend fun followWSStreams(
        request: FollowWSRequest,
        requestedBy: String,
        callContext: CallHandler<*, FollowWSResponse, *>
    ) = coroutineScope {
        val (initialJob) = db.withTransaction { jobDao.find(it, request.jobId) }
        if (initialJob.owner != requestedBy) throw RPCException("Not found", HttpStatusCode.NotFound)
        val backend = computationBackendService.getAndVerifyByName(initialJob.backend, null)

        var lastStatus: String? = null
        var lastFailedState: JobState? = null
        var lastState: JobState? = null

        var activeSubscription: Job? = null
        while (true) {
            val (job) = db.withTransaction { jobDao.find(it, request.jobId) }

            if (job.currentState == JobState.RUNNING) {
                if (activeSubscription == null) {
                    // setup a subscription
                    // TODO There is no way for us to terminate this subscription
                    activeSubscription = launch(Dispatchers.IO) {
                        var streamId: String? = null
                        callContext.withContext<WSCall> {
                            ctx.session.addOnCloseHandler {
                                val capturedStreamId = streamId
                                if (capturedStreamId != null) {
                                    backend.cancelWSStream.call(
                                        CancelWSStreamRequest(capturedStreamId),
                                        serviceClientWS
                                    )
                                }
                            }
                        }

                        backend.followWSStream.subscribe(
                            InternalFollowWSStreamRequest(
                                job,
                                request.stdoutLineStart,
                                request.stderrLineStart
                            ),
                            serviceClientWS,
                            handler = { message ->
                                println("Sending message! $message")
                                streamId = message.streamId
                                callContext.sendWSMessage(FollowWSResponse(message.stdout, message.stderr))
                            }
                        )
                    }
                }
            }

            if (lastState != job.currentState || lastStatus != job.status || lastFailedState != job.failedState) {
                lastState = job.currentState
                lastStatus = job.status
                lastFailedState = job.failedState

                callContext.sendWSMessage(
                    FollowWSResponse(
                        status = lastStatus,
                        state = lastState,
                        failedState = lastFailedState
                    )
                )
            }

            if (job.currentState.isFinal()) break

            delay(1000)
        }

        callContext.ok(FollowWSResponse())
    }
}
