package dk.sdu.cloud.project.auth.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.service.TYPE_PROPERTY

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ProjectAuthEvent.Initialized::class, name = "initialized")
)
sealed class ProjectAuthEvent {
    abstract val projectId: String

    data class Initialized(override val projectId: String) : ProjectAuthEvent()
}

object ProjectAuthEvents : EventStreamContainer() {
    val events = stream<ProjectAuthEvent>("project-auth-events", { it.projectId })
}
