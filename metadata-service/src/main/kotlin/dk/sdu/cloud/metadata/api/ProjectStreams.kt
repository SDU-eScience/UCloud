package dk.sdu.cloud.metadata.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.metadata.services.Project
import dk.sdu.cloud.service.KafkaDescriptions
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.service.MappedEventProducer
import org.apache.kafka.streams.kstream.KStream

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ProjectEvent.Created::class, name = "created")
)
sealed class ProjectEvent {
    abstract val projectId: Long

    data class Created(val project: Project) : ProjectEvent() {
        override val projectId = project.id!!
    }
}

typealias ProjectEventProducer = MappedEventProducer<Long, ProjectEvent>
typealias ProjectEventConsumer = KStream<Long, ProjectEvent>

object ProjectEvents : KafkaDescriptions() {
    val events = stream<Long, ProjectEvent>("project-events") { it.projectId }
}