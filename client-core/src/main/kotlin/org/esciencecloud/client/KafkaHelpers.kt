package org.esciencecloud.client

typealias KafkaCallDescription<R> = RESTCallDescription<R, GatewayJobResponse, GatewayJobResponse>
typealias KafkaCallDescriptionBundle<R> = List<RESTCallDescription<out R, GatewayJobResponse, GatewayJobResponse>>

// Needs to be exported to clients of GW. We purposefully remove _all_ references to Kafka here.
enum class JobStatus {
    STARTED,
    COMPLETE,
    ERROR
}

// TODO I really don't think it is a good idea for us to be leaking the details of where the message ends up.
// Should reevaluate if this is actually a good idea. Ideally our frontends will talk directly to the gateway.
// It was added because of a request from Bjørn.
class GatewayJobResponse private constructor(
        val status: JobStatus,
        val jobId: String?,
        val offset: Long?,
        val partition: Int?,
        val timestamp: Long?
) {
    companion object {
        private val error by lazy { GatewayJobResponse(JobStatus.ERROR, null, null, null, null) }
        fun started(jobId: String, offset: Long, partition: Int, timestamp: Long) =
                GatewayJobResponse(JobStatus.STARTED, jobId, offset, partition, timestamp)
        fun error() = error
    }
}
