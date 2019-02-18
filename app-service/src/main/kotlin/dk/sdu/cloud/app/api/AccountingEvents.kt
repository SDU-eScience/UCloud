package dk.sdu.cloud.app.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dk.sdu.cloud.kafka.KafkaDescriptions

data class JobCompletedEvent(
    val jobId: String,
    val jobOwner: String,
    val duration: SimpleDuration,
    val nodes: Int,
    val jobCompletedAt: Long,

    @JsonDeserialize(`as` = NameAndVersionImpl::class) val application: NameAndVersion,
    val success: Boolean
)

object AccountingEvents : KafkaDescriptions() {
    val jobCompleted = stream<String, JobCompletedEvent>("hpc.job-completed-events") { it.jobId }
}
