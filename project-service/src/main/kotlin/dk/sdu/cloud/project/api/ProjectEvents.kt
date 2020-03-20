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
    JsonSubTypes.Type(value = ProjectEvent.MemberRoleUpdated::class, name = "memberRoleUpdated"),
    JsonSubTypes.Type(value = ProjectEvent.MemberAddedToGroup::class, name = "memberAddedToGroup"),
    JsonSubTypes.Type(value = ProjectEvent.MemberRemovedFromGroup::class, name = "memberRemovedFromGroup"),
    JsonSubTypes.Type(value = ProjectEvent.GroupCreated::class, name = "groupCreated"),
    JsonSubTypes.Type(value = ProjectEvent.GroupDeleted::class, name = "groupDeleted"),
    JsonSubTypes.Type(value = ProjectEvent.GroupRenamed::class, name = "groupRenamed")
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

    data class MemberAddedToGroup(
        override val project: Project,
        val memberUsername: String,
        val newGroup: String
    ) : ProjectEvent()

    /**
     * Note this event is _not_ fired when a member is removed from the project.
     */
    data class MemberRemovedFromGroup(
        override val project: Project,
        val memberUsername: String,
        val group: String
    ) : ProjectEvent()

    data class GroupCreated(
        override val project: Project,
        val group: String
    ) : ProjectEvent()

    data class GroupDeleted(
        override val project: Project,
        val group: String
    ) : ProjectEvent()

    data class GroupRenamed(
        override val project: Project,
        val oldGroup: String,
        val newGroup: String
    ) : ProjectEvent()
}

object ProjectEvents : EventStreamContainer() {
    val events = stream<ProjectEvent>("project-events", { it.project.id })
}
