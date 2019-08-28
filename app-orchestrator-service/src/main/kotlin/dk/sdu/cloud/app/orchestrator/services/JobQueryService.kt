package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.orchestrator.api.JobSortBy
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobWithStatus
import dk.sdu.cloud.app.orchestrator.api.SortOrder
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode

class JobQueryService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: JobDao<Session>,
    private val jobFileService: JobFileService
) {
    suspend fun findById(
        user: SecurityPrincipalToken,
        jobId: String
    ): JobWithStatus {
        val (job) = db.withTransaction { session ->
            dao.findOrNull(session, jobId, user)
        } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        return job.toJobWithStatus()
    }

    suspend fun listRecent(
        user: SecurityPrincipalToken,
        pagination: NormalizedPaginationRequest,
        order: SortOrder? = null,
        sortBy: JobSortBy? = null,
        minTimestamp: Long? = null,
        maxTimestamp: Long? = null,
        state: JobState? = null,
        application: String? = null,
        version: String? = null
    ): Page<JobWithStatus> {
        return db.withTransaction {
            dao.list(
                it,
                user,
                pagination,
                order ?: SortOrder.DESCENDING,
                sortBy ?: JobSortBy.CREATED_AT,
                minTimestamp,
                maxTimestamp,
                state,
                application,
                version
            )
        }.mapItems { it.job.toJobWithStatus() }
    }

    private suspend fun VerifiedJob.toJobWithStatus(): JobWithStatus {
        val job = this
        val expiresAt = job.startedAt?.let {
            job.startedAt + job.maxTime.toMillis()
        }

        return JobWithStatus(
            job.id,
            job.name,
            job.owner,
            job.currentState,
            job.status,
            job.failedState,
            job.createdAt,
            job.modifiedAt,
            expiresAt,
            job.maxTime.toMillis(),
            jobFileService.jobFolder(job),
            job.application.metadata
        )
    }

}
