package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.app.store.api.ApplicationMetadata
import dk.sdu.cloud.app.store.api.WithAppMetadata

data class JobWithStatus(
    val jobId: String,
    val owner: String,
    val state: JobState,
    val status: String,

    @Deprecated("No longer in use")
    val appName: String,
    @Deprecated("No longer in use")
    val appVersion: String,

    val createdAt: Long,
    val modifiedAt: Long,

    override val metadata: ApplicationMetadata
) : WithAppMetadata
