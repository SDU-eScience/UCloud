package dk.sdu.cloud.project.api

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
    JsonSubTypes.Type(value = ProjectEvent.Created::class, name = "created"),
    JsonSubTypes.Type(value = ProjectEvent.Deleted::class, name = "deleted"),
    JsonSubTypes.Type(value = ProjectEvent.MemberAdded::class, name = "memberAdded"),
    JsonSubTypes.Type(value = ProjectEvent.MemberDeleted::class, name = "memberDeleted"),
    JsonSubTypes.Type(value = ProjectEvent.MemberRoleUpdated::class, name = "memberRoleUpdated")
)
sealed class ProjectEvent {
    abstract val project: Project

    data class Created(
        override val project: Project
    ) : ProjectEvent()

    data class Deleted(
        override val project: Project
    ) : ProjectEvent()

    data class MemberAdded(
        override val project: Project,
        val projectMember: ProjectMember
    ) : ProjectEvent()

    data class MemberDeleted(
        override val project: Project,
        val projectMember: ProjectMember
    ) : ProjectEvent()

    data class MemberRoleUpdated(
        override val project: Project,
        val oldRole: ProjectMember,
        val projectMember: ProjectMember
    ) : ProjectEvent()
}

object ProjectEvents : EventStreamContainer() {
    val events = stream<ProjectEvent>("project-events", { it.project.id })
}
