package dk.sdu.cloud.plugins

import dk.sdu.cloud.controllers.UserMapping
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withTransaction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class SimpleProjectPluginConfiguration(
    val groupNamespaceId: Int,
    val extensions: Extensions = Extensions()
) {
    @Serializable
    data class Extensions(
        val projectRenamed: String? = null,

        val membersAddedToProject: String? = null,
        val membersRemovedFromProject: String? = null,

        val membersRemovedFromGroup: String? = null,
        val membersAddedToGroup: String? = null,

        val projectArchived: String? = null,
        val projectUnarchived: String? = null,

        val roleChanged: String? = null,

        val groupCreated: String? = null,
        val groupRenamed: String? = null,
        val groupDeleted: String? = null,
    )
}

// NOTE(Dan, Brian): This plugin is responsible for taking the raw updates, coming in from UCloud/Core, and
// translating them into a diff. This diff is then passed on two operator defined extensions which are responsible
// for acting on these changes. In practice, this means that we store a copy of every project, in its JSON serialized
// form. We compare this with the new event and try to determine relevant changes and dispatch them to the relevant
// extensions.
//
// This plugin is also responsible for allocating unique group IDs, which are suitable for use as UNIX group IDs. The
// extensions are not required to use the allocated group IDs, but they may choose to use them if they wish.
class SimpleProjectPlugin : ProjectPlugin {
    private lateinit var pluginConfig: SimpleProjectPluginConfiguration
    private lateinit var extensions: Extensions

    override suspend fun PluginContext.initialize(pluginConfig: JsonObject) {
        this@SimpleProjectPlugin.pluginConfig = defaultMapper.decodeFromJsonElement(pluginConfig)
        this@SimpleProjectPlugin.extensions = Extensions(this@SimpleProjectPlugin.pluginConfig)
    }

    override suspend fun PluginContext.onProjectUpdated(newProject: Project) {
        val oldProject = run {
            var oldProject: Project? = null
            dbConnection.withTransaction { session ->
                session.prepareStatement(
                    """
                        select project_as_json
                        from simple_project_project_database
                        where ucloud_id = :ucloud_id
                    """
                ).useAndInvoke(
                    prepare = {
                        bindString("ucloud_id", newProject.id)
                    },
                    readRow = { row ->
                        oldProject = defaultMapper.decodeFromString(row.getString(0)!!)
                    }
                )

                session.prepareStatement(
                    """
                        insert into simple_project_project_database(ucloud_id, project_as_json)
                        values (:ucloud_id, :project_as_json)
                        on conflict (ucloud_id) do update set project_as_json = excluded.project_as_json 
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("ucloud_id", newProject.id)
                        bindString("project_as_json", defaultMapper.encodeToString(newProject))
                    }
                )
            }

            oldProject
        }

        val diff = if (oldProject == null) {
            calculateNewProjectDiffs(newProject)
        } else {
            calculateDiff(oldProject, newProject)
        }

        for (event in diff) {
            dispatchEvent(event)
        }
    }

    // NOTE(Dan, Brian): Maps a project ID to a group ID suitable for use in a UNIX system.
    //
    // The group IDs are allocated by this function, if a mapping does not exist. After which the mapping will
    // be used for eternity. The group IDs are allocated starting at a number specified in the configuration.
    private fun ucloudProjectIdToUnixGroupId(projectId: String): Int {
        return dbConnection.withTransaction { session ->
            var result: Int? = null
            session.prepareStatement(
                """
                    select local_id
                    from simple_project_group_mapper
                    where ucloud_id = :ucloud_id
                """
            ).useAndInvoke(
                prepare = {
                    bindString("ucloud_id", projectId)
                },
                readRow = { row ->
                    result = row.getInt(0)
                }
            )

            if (result != null) return@withTransaction result!! + pluginConfig.groupNamespaceId

            session.prepareStatement(
                """
                    insert into simple_project_group_mapper (ucloud_id) values (:ucloud_id) returning local_id
                """
            ).useAndInvoke(
                prepare = {
                    bindString("ucloud_id", projectId)
                },
                readRow = { row ->
                    result = row.getInt(0)
                }
            )

            check(result != null) { "Unable to allocate group ID! This should not happen." }
            return@withTransaction result!! + pluginConfig.groupNamespaceId
        }
    }

    // NOTE(Dan): See `ucloudProjectIdToUnixGroupId()`
    private fun ucloudGroupIdToUnixGroupId(groupId: String): Int {
        return ucloudProjectIdToUnixGroupId(groupId)
    }

    private fun calculateNewProjectDiffs(newProject: Project): List<ProjectDiff> {
        // NOTE(Dan): Emits events for a project which has never been seen by the system before

        val result = ArrayList<ProjectDiff>()
        val newProjectWithLocalId = ProjectWithLocalId(ucloudProjectIdToUnixGroupId(newProject.id), newProject)

        // TODO(Dan): Should we really be dispatching a renamed event?
        result.add(ProjectDiff.ProjectRenamed(null, newProjectWithLocalId, newProject.specification.title))

        val members = newProject.status.members!!
        if (members.isNotEmpty()) {
            val membersWithUid = members.map {
                val uid = UserMapping.ucloudIdToLocalId(it.username)
                ProjectMemberWithUid(uid, it)
            }

            result.add(ProjectDiff.MembersAddedToProject(null, newProjectWithLocalId, membersWithUid))

            // TODO(Dan): Should we be dispatching a role changed?
            for (member in membersWithUid) {
                result.add(
                    ProjectDiff.RoleChanged(
                        null,
                        newProjectWithLocalId,
                        member,
                        member.projectMember.role,
                        member.projectMember.role
                    )
                )
            }
        }

        val groups = newProject.status.groups!!
        val localGroups = groups.map { GroupWithLocalId(ucloudGroupIdToUnixGroupId(it.id), it) }
        if (localGroups.isNotEmpty()) {
            result.add(ProjectDiff.GroupsCreated(null, newProjectWithLocalId, localGroups))

            for (group in localGroups) {
                val groupMembers = group.group.status.members!!
                if (groupMembers.isNotEmpty()) {
                    val groupMembersWithUid = groupMembers.map {
                        val uid = UserMapping.ucloudIdToLocalId(it)
                        GroupMemberWithUid(uid, it)
                    }

                    result.add(ProjectDiff.MembersAddedToGroup(null, newProjectWithLocalId, group, groupMembersWithUid))
                }
            }
        }

        // NOTE(Dan): There is no unarchive event, since that would imply an archive event has already taken place.

        return result
    }

    private fun calculateDiff(oldProject: Project, newProject: Project): List<ProjectDiff> {
        val result = ArrayList<ProjectDiff>()

        val oldProjectWithLocalId = ProjectWithLocalId(ucloudProjectIdToUnixGroupId(oldProject.id), oldProject)
        val newProjectWithLocalId = ProjectWithLocalId(ucloudProjectIdToUnixGroupId(newProject.id), newProject)

        val oldTitle = oldProject.specification.title
        val newTitle = newProject.specification.title

        val oldMembers = oldProject.status.members!!
        val newMembers = newProject.status.members!!

        val oldGroups = oldProject.status.groups!!
        val newGroups = newProject.status.groups!!

        val oldLocalGroups = oldGroups.map { GroupWithLocalId(ucloudGroupIdToUnixGroupId(it.id), it) }
        val newLocalGroups = newGroups.map { GroupWithLocalId(ucloudGroupIdToUnixGroupId(it.id), it) }

        val oldArchived = oldProject.status.archived
        val newArchived = newProject.status.archived

        if (oldTitle != newTitle) {
            result.add(ProjectDiff.ProjectRenamed(oldProjectWithLocalId, newProjectWithLocalId, newTitle))
        }
        
        if (oldArchived != newArchived) {
            if (newArchived) {
                result.add(ProjectDiff.ProjectArchived(oldProjectWithLocalId, newProjectWithLocalId))
            } else {
                result.add(ProjectDiff.ProjectUnarchived(oldProjectWithLocalId, newProjectWithLocalId))
            }
        }

        run {
            val membersSeen = HashSet<String>()
            val addedMembers = ArrayList<ProjectMemberWithUid>()
            val removedMembers = ArrayList<ProjectMemberWithUid>()

            for (member in newMembers) {
                val memberWithUid = ProjectMemberWithUid(UserMapping.ucloudIdToLocalId(member.username), member)
                val oldStatus = oldMembers.find { it.username == member.username }
                if (oldStatus == null) {
                    addedMembers.add(memberWithUid)
                } else if (oldStatus.role != member.role) {
                    // We have a new role
                    result.add(
                        ProjectDiff.RoleChanged(
                            oldProjectWithLocalId,
                            newProjectWithLocalId,
                            memberWithUid,
                            oldStatus.role,
                            member.role
                        )
                    )
                } else {
                    // Nothing has changed
                }

                membersSeen.add(member.username)
            }

            for (member in oldMembers) {
                if (member.username !in membersSeen) {
                    val memberWithUid = ProjectMemberWithUid(UserMapping.ucloudIdToLocalId(member.username), member)
                    removedMembers.add(memberWithUid)
                }
            }

            if (addedMembers.isNotEmpty()) {
                result.add(
                    ProjectDiff.MembersAddedToProject(
                        oldProjectWithLocalId,
                        newProjectWithLocalId,
                        addedMembers
                    )
                )
            }

            if (removedMembers.isNotEmpty()) {
                result.add(
                    ProjectDiff.MembersRemovedFromProject(
                        oldProjectWithLocalId,
                        newProjectWithLocalId,
                        removedMembers
                    )
                )
            }
        }

        run {
            val groupsSeen = HashSet<String>()
            val addedGroups = ArrayList<GroupWithLocalId>()
            val removedGroups = ArrayList<GroupWithLocalId>()

            for (group in newLocalGroups) {
                val oldStatus = oldLocalGroups.find { it.group.id == group.group.id }

                if (oldStatus != null && oldStatus.group.specification.title != group.group.specification.title) {
                    result.add(ProjectDiff.GroupRenamed(oldProjectWithLocalId, newProjectWithLocalId, group))
                }

                if (oldStatus == null) {
                    addedGroups.add(group)
                    val membersWithUid = group.group.status.members!!.map {
                        GroupMemberWithUid(UserMapping.ucloudIdToLocalId(it), it)
                    }

                    if (membersWithUid.isNotEmpty()) {
                        result.add(
                            ProjectDiff.MembersAddedToGroup(
                                oldProjectWithLocalId,
                                newProjectWithLocalId,
                                group,
                                membersWithUid
                            )
                        )
                    }
                } else {
                    val addedMembers = ArrayList<String>()
                    val removedMembers = ArrayList<String>()

                    val oldGroupMembers = oldStatus.group.status.members!!
                    val newGroupMembers = group.group.status.members!!

                    for (member in newGroupMembers) {
                        if (member !in oldGroupMembers) {
                            addedMembers.add(member)
                        }
                    }

                    for (member in oldGroupMembers) {
                        if (member !in newGroupMembers) {
                            removedMembers.add(member)
                        }
                    }

                    if (addedMembers.isNotEmpty()) {
                        result.add(
                            ProjectDiff.MembersAddedToGroup(
                                oldProjectWithLocalId,
                                newProjectWithLocalId,
                                group,
                                addedMembers.map { GroupMemberWithUid(UserMapping.ucloudIdToLocalId(it), it) }
                            )
                        )
                    }

                    if (removedMembers.isNotEmpty()) {
                        result.add(
                            ProjectDiff.MembersRemovedFromGroup(
                                oldProjectWithLocalId,
                                newProjectWithLocalId,
                                group,
                                removedMembers.map { GroupMemberWithUid(UserMapping.ucloudIdToLocalId(it), it) }
                            )
                        )
                    }
                }

                groupsSeen.add(group.group.id)
            }

            for (group in oldLocalGroups) {
                if (group.group.id !in groupsSeen) {
                    removedGroups.add(group)
                }
            }

            if (addedGroups.isNotEmpty()) {
                result.add(ProjectDiff.GroupsCreated(oldProjectWithLocalId, newProjectWithLocalId, addedGroups))
            }

            if (removedGroups.isNotEmpty()) {
                result.add(ProjectDiff.GroupsDeleted(oldProjectWithLocalId, newProjectWithLocalId, removedGroups))
            }
        }

        // NOTE(Dan): Ordering is quite important. We should dispatch events in an order that makes sense, which is
        // not the same order as we compute them. For example, we should dispatch group creation before members
        // added to group.

        return result.sortedBy { event ->
            when (event)  {
                is ProjectDiff.ProjectArchived -> 1
                is ProjectDiff.ProjectUnarchived -> 2

                is ProjectDiff.MembersRemovedFromProject -> 3
                is ProjectDiff.ProjectRenamed -> 4
                is ProjectDiff.RoleChanged -> 5

                is ProjectDiff.GroupsCreated -> 6
                is ProjectDiff.GroupRenamed -> 7
                is ProjectDiff.GroupsDeleted -> 8

                is ProjectDiff.MembersAddedToGroup -> 9
                is ProjectDiff.MembersAddedToProject -> 10
                is ProjectDiff.MembersRemovedFromGroup -> 11
            }
        }
    }

    private fun dispatchEvent(event: ProjectDiff) {
        when (event) {
            is ProjectDiff.GroupRenamed -> extensions.groupRenamed.optionalInvoke(event)
            is ProjectDiff.GroupsCreated -> extensions.groupCreated.optionalInvoke(event)
            is ProjectDiff.GroupsDeleted -> extensions.groupDeleted.optionalInvoke(event)
            is ProjectDiff.MembersAddedToGroup -> extensions.membersAddedToGroup.optionalInvoke(event)
            is ProjectDiff.MembersAddedToProject -> extensions.membersAddedToProject.optionalInvoke(event)
            is ProjectDiff.MembersRemovedFromGroup -> extensions.membersRemovedFromGroup.optionalInvoke(event)
            is ProjectDiff.MembersRemovedFromProject -> extensions.membersRemovedFromProject.optionalInvoke(event)
            is ProjectDiff.ProjectArchived -> extensions.projectArchived.optionalInvoke(event)
            is ProjectDiff.ProjectRenamed -> extensions.projectRenamed.optionalInvoke(event)
            is ProjectDiff.ProjectUnarchived -> extensions.projectUnarchived.optionalInvoke(event)
            is ProjectDiff.RoleChanged -> extensions.roleChanged.optionalInvoke(event)
        }
    }

    private class Extensions(val pluginConfig: SimpleProjectPluginConfiguration) {
        private val e = pluginConfig.extensions

        val projectRenamed = optionalExtension<ProjectDiff, Unit>(e.projectRenamed)

        val membersAddedToProject = optionalExtension<ProjectDiff, Unit>(e.membersAddedToProject)
        val membersRemovedFromProject = optionalExtension<ProjectDiff, Unit>(e.membersRemovedFromProject)

        val membersRemovedFromGroup = optionalExtension<ProjectDiff, Unit>(e.membersRemovedFromGroup)
        val membersAddedToGroup = optionalExtension<ProjectDiff, Unit>(e.membersAddedToGroup)

        val projectArchived = optionalExtension<ProjectDiff, Unit>(e.projectArchived)
        val projectUnarchived = optionalExtension<ProjectDiff, Unit>(e.projectUnarchived)

        val roleChanged = optionalExtension<ProjectDiff, Unit>(e.roleChanged)

        val groupCreated = optionalExtension<ProjectDiff, Unit>(e.groupCreated)
        val groupRenamed = optionalExtension<ProjectDiff, Unit>(e.groupRenamed)
        val groupDeleted = optionalExtension<ProjectDiff, Unit>(e.groupDeleted)
    }
}

@Serializable
private sealed class ProjectDiff {
    abstract val oldProject: ProjectWithLocalId?
    abstract val newProject: ProjectWithLocalId

    @Serializable
    @SerialName("project_renamed")
    data class ProjectRenamed(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
        val newTitle: String
    ) : ProjectDiff()

    @Serializable
    @SerialName("members_added_to_project")
    data class MembersAddedToProject(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
        val newMembers: List<ProjectMemberWithUid>,
    ) : ProjectDiff()

    @Serializable
    @SerialName("members_removed_from_project")
    data class MembersRemovedFromProject(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
        val removedMembers: List<ProjectMemberWithUid>
    ) : ProjectDiff()

    @Serializable
    @SerialName("members_added_to_group")
    data class MembersAddedToGroup(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
        val group: GroupWithLocalId,
        val newMembers: List<GroupMemberWithUid>
    ) : ProjectDiff()

    @Serializable
    @SerialName("members_removed_from_group")
    data class MembersRemovedFromGroup(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
        val group: GroupWithLocalId,
        val removedMembers: List<GroupMemberWithUid>
    ) : ProjectDiff()

    @Serializable
    @SerialName("project_archived")
    data class ProjectArchived(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
    ) : ProjectDiff()

    @Serializable
    @SerialName("project_unarchived")
    data class ProjectUnarchived(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
    ) : ProjectDiff()

    @Serializable
    @SerialName("role_changed")
    data class RoleChanged(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
        val member: ProjectMemberWithUid,
        val oldRole: ProjectRole,
        val newRole: ProjectRole
    ) : ProjectDiff()

    @Serializable
    @SerialName("group_created")
    data class GroupsCreated(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
        val groups: List<GroupWithLocalId>
    ) : ProjectDiff()

    @Serializable
    @SerialName("group_deleted")
    data class GroupsDeleted(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
        val groups: List<GroupWithLocalId>
    ) : ProjectDiff()

    @Serializable
    @SerialName("group_renamed")
    data class GroupRenamed(
        override val oldProject: ProjectWithLocalId?,
        override val newProject: ProjectWithLocalId,
        val group: GroupWithLocalId,
    ) : ProjectDiff()
}

@Serializable
private data class GroupWithLocalId(
    val localId: Int,
    val group: Group
)

@Serializable
private data class ProjectWithLocalId(
    val localId: Int,
    val project: Project
)

@Serializable
private data class ProjectMemberWithUid(
    val uid: Int?,
    val projectMember: ProjectMember
)

@Serializable
private data class GroupMemberWithUid(
    val uid: Int?,
    val ucloudUsername: String
)
