package dk.sdu.cloud.abc.api

enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILURE
}

class RunningJobStatus(val jobId: String, val status: JobStatus)
