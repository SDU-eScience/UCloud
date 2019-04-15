package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.FollowStdStreamsRequest
import dk.sdu.cloud.app.api.FollowStdStreamsResponse
import dk.sdu.cloud.app.api.InternalFollowStdStreamsRequest
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class StreamFollowService<DBSession>(
    private val jobFileService: JobFileService,
    private val serviceClient: AuthenticatedClient,
    private val computationBackendService: ComputationBackendService,
    private val db: DBSessionFactory<DBSession>,
    private val jobDao: JobDao<DBSession>
) {
    suspend fun followStreams(
        request: FollowStdStreamsRequest
    ): FollowStdStreamsResponse {
        val (job, _) = db.withTransaction { jobDao.find(it, request.jobId) }
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
            job.id,
            jobFileService.jobFolder(job),
            job.application.metadata
        )
    }
}