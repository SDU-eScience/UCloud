package dk.sdu.cloud.app.api

data class JobWithStatus(
    val jobId: String,
    val owner: String,
    val state: AppState,
    val status: String,
    val appName: String,
    val appVersion: String,

    val createdAt: Long,
    val modifiedAt: Long
)
