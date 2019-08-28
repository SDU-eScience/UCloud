package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.app.store.api.ApplicationMetadata
import dk.sdu.cloud.app.store.api.WithAppMetadata

data class JobWithStatus(
    val jobId: String,
    val name: String?,
    val owner: String,
    val state: JobState,
    val status: String,
    val failedState: JobState?,

    val createdAt: Long,
    val modifiedAt: Long,
    val expiresAt: Long?,
    val maxTime: Long,
    val outputFolder: String?,

    override val metadata: ApplicationMetadata
) : WithAppMetadata
