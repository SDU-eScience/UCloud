package dk.sdu.cloud.app.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

interface JobDao<Session> {
    fun create(
        session: Session,
        jobWithToken: VerifiedJobWithAccessToken
    )

    fun updateStateAndStatus(
        session: Session,
        systemId: String,
        state: JobState,
        status: String? = null
    )

    fun updateStatus(
        session: Session,
        systemId: String,
        status: String
    )

    fun updateWorkspace(
        session: Session,
        systemId: String,
        workspace: String
    )

    fun findOrNull(
        session: Session,
        systemId: String,
        owner: SecurityPrincipalToken? = null
    ): VerifiedJobWithAccessToken?

    fun list(
        session: Session,
        owner: SecurityPrincipalToken,
        pagination: NormalizedPaginationRequest
    ): Page<VerifiedJobWithAccessToken>

    suspend fun findJobsCreatedBefore(
        session: Session,
        timestamp: Long
    ): Sequence<VerifiedJobWithAccessToken>
}

