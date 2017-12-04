package org.esciencecloud.transactions

import org.apache.kafka.clients.producer.RecordMetadata

// -------------------------------------------
// These should be exported to clients
// -------------------------------------------

enum class JobStatus {
    STARTED,
    COMPLETE,
    ERROR
}

class GatewayJobResponse private constructor(
        val status: JobStatus,
        val jobId: String?,
        metadata: RecordMetadata?
) {
    val offset = metadata?.offset()
    val partition = metadata?.partition()
    val timestamp = metadata?.timestamp()

    companion object {
        private val error by lazy { GatewayJobResponse(JobStatus.ERROR, null, null) }
        fun started(jobId: String, metadata: RecordMetadata) = GatewayJobResponse(JobStatus.STARTED, jobId, metadata)
        fun error() = error
    }
}
