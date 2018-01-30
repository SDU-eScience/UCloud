package dk.sdu.cloud.app.api

data class JobWithStatus(
        val jobId: String,
        val owner: String,
        val status: JobStatus,
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
        val workingDirectory: String,
        val jobDirectory: String
)