package dk.sdu.cloud.app.api

import dk.sdu.cloud.service.KafkaDescriptions

class JobCompletedEvent(
    val jobId: String,
    val jobOwner: String,
    val duration: SimpleDuration,
    val nodes: Int,
    val jobCompletedAt: Long,

    val application: NameAndVersion,
    val success: Boolean
)

object AccountingEvents : KafkaDescriptions() {
    val jobCompleted = stream<String, JobCompletedEvent>("hpc.job-completed-events") { it.jobId }
}
