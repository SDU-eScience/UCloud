package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonIgnore

data class JobWithStatus(
    val jobId: String,
    val owner: String,
    val status: AppState,
    val appName: String,
    val appVersion: String,

    val createdAt: Long,
    val modifiedAt: Long
)

data class JobWithStatusAndInvocation(
    val jobInfo: JobWithStatus,
    val parameters: Map<String, Any>,
    val appName: String,
    val appVersion: String,
    @get:JsonIgnore
    val workingDirectory: String,
    @get:JsonIgnore
    val jobDirectory: String
)