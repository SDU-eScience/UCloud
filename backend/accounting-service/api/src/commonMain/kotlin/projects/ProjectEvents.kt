package dk.sdu.cloud.project.api

import dk.sdu.cloud.events.EventStreamContainer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ProjectEvent {
    abstract val projectId: String

    @Serializable
    @SerialName("created")
    data class Created(
        override val projectId: String
    ) : ProjectEvent()

    @Serializable
    @SerialName("deleted")
    data class Deleted(
        override val projectId: String
    ) : ProjectEvent()

    @Serializable
    @SerialName("memberAdded")
    data class MemberAdded(
        override val projectId: String,
        val projectMember: ProjectMember
    ) : ProjectEvent()

    @Serializable
    @SerialName("memberDeleted")
    data class MemberDeleted(
        override val projectId: String,
        val projectMember: ProjectMember
    ) : ProjectEvent()

    @Serializable
    @SerialName("memberRoleUpdated")
    data class MemberRoleUpdated(
        override val projectId: String,
        val oldRole: ProjectMember,
        val projectMember: ProjectMember
    ) : ProjectEvent()

    @Serializable
    @SerialName("memberAddedToGroup")
    data class MemberAddedToGroup(
        override val projectId: String,
        val memberUsername: String,
        val newGroup: String
    ) : ProjectEvent()

    /**
     * Note this event is _not_ fired when a member is removed from the project.
     */
    @Serializable
    @SerialName("memberRemovedFromGroup")
    data class MemberRemovedFromGroup(
        override val projectId: String,
        val memberUsername: String,
        val group: String
    ) : ProjectEvent()

    @Serializable
    @SerialName("groupCreated")
    data class GroupCreated(
        override val projectId: String,
        val group: String
    ) : ProjectEvent()

    @Serializable
    @SerialName("groupDeleted")
    data class GroupDeleted(
        override val projectId: String,
        val group: String
    ) : ProjectEvent()

    @Serializable
    @SerialName("groupRenamed")
    data class GroupRenamed(
        override val projectId: String,
        val oldGroup: String,
        val newGroup: String
    ) : ProjectEvent()
}

object ProjectEvents : EventStreamContainer() {
    val events = stream<ProjectEvent>("project-events", { it.projectId })
}
