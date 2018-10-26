package dk.sdu.cloud.app.services

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

    fun findOrNull(
        session: Session,
        systemId: String,
        owner: String? = null
    ): VerifiedJobWithAccessToken?

    fun list(
        session: Session,
        owner: String,
        pagination: NormalizedPaginationRequest
    ): Page<VerifiedJobWithAccessToken>
}

