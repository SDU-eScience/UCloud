package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.JobState

interface JobDao<Session> {
    fun create(
        session: Session,
        jobWithToken: VerifiedJobWithAccessToken
    )

    fun updateState(
        session: Session,
        systemId: String,
        state: JobState
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
}

