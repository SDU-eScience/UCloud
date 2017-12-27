package dk.sdu.cloud.app.api

enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILURE
}

class RunningJobStatus(val jobId: String, val status: JobStatus)
