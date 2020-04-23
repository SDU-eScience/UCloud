package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipalToken
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
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StreamFollowService(
    private val jobFileService: JobFileService,
    private val serviceClient: AuthenticatedClient,
    private val serviceClientWS: AuthenticatedClient,
    private val computationBackendService: ComputationBackendService,
    private val jobQueryService: JobQueryService<*>,
    private val backgroundScope: BackgroundScope
) {
    suspend fun followStreams(
        request: FollowStdStreamsRequest,
        requestedBy: SecurityPrincipalToken
    ): FollowStdStreamsResponse {
        val jobWithToken = jobQueryService.findById(requestedBy, request.jobId)
        val (job) = jobWithToken

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
            jobFileService.jobFolder(jobWithToken),
            job.application.metadata
        )
    }

    suspend fun followWSStreams(
        request: FollowWSRequest,
        requestedBy: SecurityPrincipalToken,
        callContext: CallHandler<*, FollowWSResponse, *>
    ): Job {
        fun debug(message: String) {
            log.debug("[${request.jobId}] $message")
        }

        return backgroundScope.launch {
            debug("Initializing stream")

            val (initialJob) = jobQueryService.findById(requestedBy, request.jobId)
            val backend = computationBackendService.getAndVerifyByName(initialJob.backend, null)

            var lastStatus: String? = null
            var lastFailedState: JobState? = null
            var lastState: JobState? = null
            var streamId: String? = null

            suspend fun cancelStream() {
                val capturedStreamId = streamId
                if (capturedStreamId != null) {
                    backend.cancelWSStream.call(
                        CancelWSStreamRequest(capturedStreamId),
                        serviceClientWS
                    )
                }
            }

            var activeSubscription: Job? = null
            while (true) {
                try {
                    val (job) = jobQueryService.findById(requestedBy, request.jobId)

                    if (job.currentState == JobState.RUNNING) {
                        if (activeSubscription == null) {
                            // setup a subscription
                            activeSubscription = launch {
                                callContext.withContext<WSCall> {
                                    ctx.session.addOnCloseHandler { cancelStream() }
                                }

                                backend.followWSStream.subscribe(
                                    InternalFollowWSStreamRequest(
                                        job,
                                        request.stdoutLineStart,
                                        request.stderrLineStart
                                    ),
                                    serviceClientWS,
                                    handler = { message ->
                                        streamId = message.streamId
                                        debug("Sending new log message")
                                        callContext.sendWSMessage(
                                            FollowWSResponse(
                                                message.stdout,
                                                message.stderr
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (job.currentState.isFinal()) {
                        cancelStream()
                    }

                    if (lastState != job.currentState || lastStatus != job.status || lastFailedState != job.failedState) {
                        debug("Sending updated state information")

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

                    if (job.currentState.isFinal()) {
                        debug("Job is done!")
                        break
                    }

                    delay(1000)
                } catch (ex: Throwable) {
                    if (ex !is ClosedReceiveChannelException && ex.cause !is ClosedReceiveChannelException) {
                        log.info(ex.stackTraceToString())
                    }
                    break
                }
            }

            debug("End of stream")
            callContext.ok(FollowWSResponse())
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
