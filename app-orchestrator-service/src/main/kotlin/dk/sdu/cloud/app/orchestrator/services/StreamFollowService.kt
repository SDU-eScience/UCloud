package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.orchestrator.api.FollowStdStreamsRequest
import dk.sdu.cloud.app.orchestrator.api.FollowStdStreamsResponse
import dk.sdu.cloud.app.orchestrator.api.InternalFollowStdStreamsRequest
import dk.sdu.cloud.app.orchestrator.services.JobFileService
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import kotlin.math.max

class StreamFollowService<DBSession>(
    private val jobFileService: JobFileService,
    private val serviceClient: AuthenticatedClient,
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
}
