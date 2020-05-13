package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode

class JobQueryService(
    private val db: DBContext,
    private val dao: JobDao,
    private val jobFileService: JobFileService,
    private val projectCache: ProjectCache
) {
    suspend fun findById(
        user: SecurityPrincipalToken,
        jobId: String
    ): VerifiedJobWithAccessToken {
        val verifiedJobWithToken = dao.find(db, listOf(jobId), null).singleOrNull()
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        // We just validate that we can view the entries from this (since we know which job to look for concretely)
        if (verifiedJobWithToken.job.owner != user.principal.username) {
            val project = verifiedJobWithToken.job.project ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            val role = projectCache.retrieveRole(user.principal.username, project)
            if (role?.isAdmin() != true) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }
        }

        return verifiedJobWithToken
    }

    suspend fun listRecent(
        user: SecurityPrincipalToken,
        pagination: NormalizedPaginationRequest,
        query: JobQuery,
        projectContext: ProjectContext? = null
    ): Page<JobWithStatus> {
        // We pass the project context eagerly to the list
        return dao.list(
            db,
            user.principal.username,
            pagination,
            query,
            projectContext
        ).mapItems { asJobWithStatus(it) }
    }

    suspend fun asJobWithStatus(verifiedJobWithAccessToken: VerifiedJobWithAccessToken): JobWithStatus {
        val (job) = verifiedJobWithAccessToken
        val startedAt = job.startedAt
        val expiresAt = startedAt?.let {
            startedAt + job.maxTime.toMillis()
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
            jobFileService.jobFolder(verifiedJobWithAccessToken),
            job.application.metadata
        )
    }
}
