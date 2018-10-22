package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppState
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.VerifiedJob

data class NewJobInformation(
    val systemId: String,
    val owner: String,
    val application: NameAndVersion,
    val status: String,
    val state: JobState,
    val accessToken: String,
    val nodes: Int,

    // Dynamic data (likely to be stored as JSON)
    // TODO This feels weird, we won't easily get what we want
    val rawParameters: Map<String, Any?>,
    val backendName: String,

    // Timestamps
    val createdAt: Long,
    val modifiedAt: Long
)

data class SlurmJobData(
    val sshUser: String?,
    val slurmId: Long?,
    val jobDirectory: String?,
    val workingDirectory: String?
)

interface NewJobDao<Session> {
    fun create(
        session: Session,
        job: VerifiedJob
    )

    fun updateState(
        session: Session,
        systemId: String,
        state: AppState
    )

    fun updateStatus(
        session: Session,
        systemId: String,
        status: String
    )

    fun findOrNull(
        session: Session,
        systemId: String,
        owner: String
    ): NewJobInformation?
}

