package org.esciencecloud.abc.api

enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILURE
}

class RunningJobStatus(val jobId: String, val status: JobStatus)
