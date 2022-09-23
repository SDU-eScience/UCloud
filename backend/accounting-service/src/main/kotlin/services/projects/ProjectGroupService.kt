package dk.sdu.cloud.accounting.services.projects

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.util.ProjectCache
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.project.api.v2.Group
import dk.sdu.cloud.project.api.v2.GroupMember
import dk.sdu.cloud.project.api.v2.ProjectsRenameGroupRequestItem
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.accounting.services.projects.v2.ProjectService as P2Service

object GroupTable : SQLTable("project.groups") {
    val id = text("id")
    val project = text("project")
    val title = text("title")
}

object GroupMembershipTable : SQLTable("project.group_members") {
    val group = text("group_id")
    val username = text("username")
}

class ProjectGroupService(
    private val projectCache: ProjectCache,
    private val p2: P2Service,
) {
    suspend fun createGroup(
        ctx: DBContext,
        createdBy: String,
        projectId: String,
        group: String
    ): String {
        return p2.createGroup(
            ActorAndProject(Actor.SystemOnBehalfOfUser(createdBy), projectId),
            bulkRequestOf(Group.Specification(projectId, group)),
            ctx = ctx
        ).responses.first().id
    }

    suspend fun deleteGroups(
        ctx: DBContext,
        deletedBy: String,
        projectId: String,
        groups: Set<String>
    ) {
        p2.deleteGroup(
            ActorAndProject(Actor.SystemOnBehalfOfUser(deletedBy), projectId),
            BulkRequest(groups.map { FindByStringId(it) }),
            ctx = ctx
        )
    }

    suspend fun addMember(
        ctx: DBContext,
        addedBy: String,
        projectId: String,
        groupId: String,
        newMember: String
    ) {
        p2.createGroupMember(
            ActorAndProject(Actor.SystemOnBehalfOfUser(addedBy), projectId),
            bulkRequestOf(
                GroupMember(
                    newMember,
                    groupId
                )
            ),
            ctx = ctx
        )

        projectCache.invalidate(newMember)
    }

    suspend fun removeMember(
        ctx: DBContext,
        removedBy: String,
        projectId: String,
        groupId: String,
        memberToRemove: String
    ) {
        p2.deleteGroupMember(
            ActorAndProject(Actor.SystemOnBehalfOfUser(removedBy), projectId),
            bulkRequestOf(
                GroupMember(
                    memberToRemove,
                    groupId
                )
            ),
            ctx = ctx
        )

        projectCache.invalidate(memberToRemove)
    }

    suspend fun rename(
        ctx: DBContext,
        actor: Actor,
        groupId: String,
        newName: String
    ) {
        p2.renameGroup(
            ActorAndProject(actor, null),
            bulkRequestOf(
                ProjectsRenameGroupRequestItem(groupId, newName)
            ),
            ctx = ctx
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
