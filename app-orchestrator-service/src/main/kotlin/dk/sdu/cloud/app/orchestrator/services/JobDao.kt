package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.orchestrator.api.JobQuery
import dk.sdu.cloud.app.orchestrator.api.JobSortBy
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.SortOrder
import dk.sdu.cloud.project.api.ProjectRole

import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

data class ProjectContext(val project: String, val role: ProjectRole)

interface JobDao<Session> {
    fun create(
        session: Session,
        jobWithToken: VerifiedJobWithAccessToken
    )

    fun updateStateAndStatus(
        session: Session,
        systemId: String,
        state: JobState,
        status: String? = null,
        failedState: JobState? = null
    )

    fun updateStatus(
        session: Session,
        systemId: String,
        status: String
    )

    suspend fun find(
        session: Session,
        systemIds: List<String>,
        owner: SecurityPrincipalToken? = null
    ): List<VerifiedJobWithAccessToken>

    suspend fun list(
        session: Session,
        owner: SecurityPrincipalToken,
        pagination: NormalizedPaginationRequest,
        query: JobQuery,
        projectContext: ProjectContext? = null
    ): Page<VerifiedJobWithAccessToken>

    suspend fun list10LatestActiveJobsOfApplication(
        session: Session,
        owner: SecurityPrincipalToken,
        application: String,
        version: String
    ): List<VerifiedJobWithAccessToken>

    suspend fun findJobsCreatedBefore(
        session: Session,
        timestamp: Long
    ): Sequence<VerifiedJobWithAccessToken>

    suspend fun findFromUrlId(
        session: Session,
        urlId: String,
        owner: SecurityPrincipalToken? = null
    ): VerifiedJobWithAccessToken?

    suspend fun isUrlOccupied(
        session: Session,
        urlId: String
    ): Boolean
}

suspend fun <Session> JobDao<Session>.findOrNull(
    session: Session,
    systemId: String,
    owner: SecurityPrincipalToken? = null
): VerifiedJobWithAccessToken? {
    return find(session, listOf(systemId), owner).singleOrNull { it.job.id == systemId }
}
