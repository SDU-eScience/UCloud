package dk.sdu.cloud.plugins

import dk.sdu.cloud.controllers.UserMapping
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.config.*
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.sql.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

// NOTE(Dan, Brian): This plugin is responsible for taking the raw updates, coming in from UCloud/Core, and
// translating them into a diff. This diff is then passed on to operator defined extensions which are responsible
// for acting on these changes. These extensions are defined in the configuration (see above). In practice, this means
// that we store a copy of every project, in its JSON serialized form. We compare this with the new event and try to
// determine relevant changes and dispatch them to the relevant extensions.
//
// This plugin is also responsible for allocating unique group IDs, which are suitable for use as UNIX group IDs. The
// extensions are not required to use the allocated group IDs, but they may choose to use them if they wish.
class SimpleProjectPlugin : ProjectPlugin {
    private lateinit var pluginConfig: ConfigSchema.Plugins.Projects.Simple
    private lateinit var extensions: Extensions

    override fun configure(config: ConfigSchema.Plugins.Projects) {
        this.pluginConfig = config as ConfigSchema.Plugins.Projects.Simple
        this.extensions = Extensions(this.pluginConfig)
    }

    // NOTE(Dan): Invoked whenever an update arrives from UCloud/Core. The update simply contains the new state of a
    // project which has changed. The integration module isn't guaranteed to receive an invocation per update in
    // UCloud/Core. This can, for example, happen if the integration module is down. As a result, each update can
    // result in many diffs or even none at all.
    override suspend fun PluginContext.onProjectUpdated(newProject: Project) {
        val oldProject = run {
            var oldProject: Project? = null
            dbConnection.withSession { session ->
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

        // NOTE(Dan): Diffs are slightly different if we have an old project or not.
        val diff = if (oldProject == null) {
            calculateNewProjectDiffs(newProject)
        } else {
            calculateDiff(oldProject, newProject)
        }

        for (event in diff) {
            try {
                dispatchEvent(event)
            } catch (ex: Throwable) {
                log.warn("Failed to dispatch event: ${ex.message}")
            }
        }

        for (event in diff) {
            when (event) {
                is ProjectDiff.MembersAddedToProject -> {
                    for (member in event.newMembers) {
                        if (member.uid != null) {
                            this.ipcServer.requestClientRestart(member.uid)
                        }
                    }
                }

                is ProjectDiff.MembersAddedToGroup -> {
                    for (member in event.newMembers) {
                        if (member.uid != null) {
                            this.ipcServer.requestClientRestart(member.uid)
                        }
                    }
                }

                is ProjectDiff.MembersRemovedFromProject -> {
                    for (member in event.removedMembers) {
                        if (member.uid != null) {
                            this.ipcServer.requestClientRestart(member.uid)
                        }
                    }
                }

                is ProjectDiff.MembersRemovedFromGroup -> {
                    for (member in event.removedMembers) {
                        if (member.uid != null) {
                            this.ipcServer.requestClientRestart(member.uid)
                        }
                    }
                }

                else -> {
                    // do nothing
                }
            }
        }

        // NOTE(Dan): Calculate project members we don't know the UID of. We need to register these in a database such
        // that we can correctly enroll them into groups later.
        val missingUids = ArrayList<Map<String, String>>()
        for (event in diff) {
            when (event) {
                is ProjectDiff.MembersAddedToProject -> {
                    for (member in event.newMembers) {
                        if (member.uid == null) {
                            missingUids.add(mapOf("username" to member.projectMember.username))
                        } else {
                            this.ipcServer.requestClientRestart(member.uid)
                        }
                    }
                }

                is ProjectDiff.MembersAddedToGroup -> {
                    for (member in event.newMembers) {
                        if (member.uid == null) {
                            missingUids.add(mapOf("username" to member.ucloudUsername))
                        } else {
                            this.ipcServer.requestClientRestart(member.uid)
                        }
                    }
                }

                else -> {
                    // Nothing to do. We purposefully don't attempt to remove entries which are no longer valid.
                    // Instead, we simply keep them. We also keep a timestamp such that we could potentially remove
                    // old and irrelevant data.
                }
            }
        }

        if (!missingUids.isEmpty()) {
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        with changes as (
                            ${safeSqlTableUpload("changes", missingUids)}
                        )
                        insert or ignore into simple_project_missing_connections(ucloud_id, project_id)
                        select c.username, :project_id
                        from changes c
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindTableUpload("changes", missingUids)
                        bindString("project_id", newProject.id)
                    }
                )
            }
        }
    }

    override suspend fun PluginContext.onUserMappingInserted(ucloudId: String, localId: Int) {
        fixMissingConnections(ucloudId, localId)
    }

    override suspend fun PluginContext.lookupLocalId(ucloudId: String): Int? {
        var result: Int? = null
        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    select local_id
                    from simple_project_group_mapper
                    where ucloud_id = :ucloud_id
                """
            ).useAndInvoke(
                prepare = {
                    bindString("ucloud_id", ucloudId)
                },
                readRow = { row ->
                    result = row.getInt(0)
                }
            )
        }
        if (result == null) return null
        return result!! + pluginConfig.unixGroupNamespace
    }

    // NOTE(Brian): Called when a new user-mapping is inserted. Will dispatch UserAddedToProject and UserAddedToGroup
    // events to the extension, fixing any missing connections between the user and projects/groups.
    private suspend fun fixMissingConnections(userId: String, localId: Int) {
        val projects = mutableSetOf<Project>()
        dbConnection.withSession { session ->

            // Fetch missing connections
            session.prepareStatement(
                """
                    select p.project_as_json
                    from simple_project_missing_connections m
                    left join simple_project_project_database p
                    on m.project_id = p.ucloud_id
                    where m.ucloud_id = :user_id
                """
            ).useAndInvoke(
                prepare = {
                    bindString("user_id", userId)
                },
                readRow = { row ->
                    val project: Project? = defaultMapper.decodeFromString(row.getString(0)!!)
                    if (project != null) {
                        if (project.status.members!!.map { it.username }.contains(userId)) {
                            projects.add(project)
                        }
                    }
                }
            )

            // Dispatch events
            projects.forEach { project ->
                val projectWithLocalId = ProjectWithLocalId(ucloudProjectIdToUnixGroupId(project.id), project)
                val event = ProjectDiff.MembersAddedToProject(
                    projectWithLocalId,
                    projectWithLocalId,
                    listOf(
                        ProjectMemberWithUid(
                            localId,
                            project.status.members!!.first { it.username == userId }
                        )
                    )
                )

                try {
                    dispatchEvent(event)
                } catch (ex: Throwable) {
                    log.warn("Extension failed when attempting to add a user whom was recently connected to a UCloud " +
                        "project. The plugin will proceed resolving other missing connections to projects and groups.")
                    log.warn("${ex.message}")
                }

                project.status.groups!!.forEach { group ->
                    if (group.status.members!!.contains(userId)) {
                        val event = ProjectDiff.MembersAddedToGroup(
                            projectWithLocalId,
                            projectWithLocalId,
                            GroupWithLocalId(ucloudGroupIdToUnixGroupId(group.id), group),
                            listOf(
                                GroupMemberWithUid(
                                    localId,
                                    userId
                                )
                            )
                        )

                        try {
                            dispatchEvent(event)
                        } catch (ex: Throwable) {
                            log.warn("Extension failed when attempting to add a user whom was recently connected to " +
                                "a UCloud project group. The plugin will proceed resolving other missing connections " +
                                "to projects and groups.")
                            log.warn("${ex.message}")
                        }
                    }
                }
            }

            // Clean up (the user is no longer missing)
            session.prepareStatement(
                """
                    delete from 
                    simple_project_missing_connections
                    where ucloud_id = :user_id
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("user_id", userId)
                }
            )
        }
    }

    // NOTE(Dan, Brian): Maps a project ID to a group ID suitable for use in a UNIX system.
    //
    // The group IDs are allocated by this function, if a mapping does not exist. After which the mapping will
    // be used for eternity. The group IDs are allocated starting at a number specified in the configuration.
    private suspend fun ucloudProjectIdToUnixGroupId(projectId: String): Int {
        return dbConnection.withSession { session ->
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

            if (result != null) return@withSession result!! + pluginConfig.unixGroupNamespace

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
            return@withSession result!! + pluginConfig.unixGroupNamespace
        }
    }

    // NOTE(Dan): See `ucloudProjectIdToUnixGroupId()`
    private suspend fun ucloudGroupIdToUnixGroupId(groupId: String): Int {
        return ucloudProjectIdToUnixGroupId(groupId)
    }

    private suspend fun calculateDiff(oldProject: Project, newProject: Project): List<ProjectDiff> {
        // NOTE(Dan): The result will contain the diff events as we calculate them. We return these at the end of the
        // function. We sort this list at the end of this function. As a result, please don't return early unless
        // it is an unrecoverable error.
        val result = ArrayList<ProjectDiff>()

        // Just a bunch of aliases and UID/GID mappings
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

        // NOTE(Dan): From this point, we actually calculate the diff. You can look at the bottom of this file for a
        // concrete list of `ProjectDiff`s. For the most part, the events directly mirror the changes which can occur
        // when a project admin performs a management action. For the operator's convenience, we also perform UID and
        // GID mapping in this function. That way the operator should have all the information required to write
        // an extension with very little code required.
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

        return result.doSort()
    }

    // NOTE(Dan): Emits events for a project which has never been seen by the system before
    private suspend fun calculateNewProjectDiffs(newProject: Project): List<ProjectDiff> {
        // NOTE(Dan): The result list is sorted before existing the function. Please don't return early.
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

        return result.doSort()
    }

    private fun List<ProjectDiff>.doSort(): List<ProjectDiff> {
        // NOTE(Dan): Ordering is quite important. We should dispatch events in an order that makes sense, which is
        // not the same order as we compute them. For example, we should dispatch group creation before members
        // added to group.
        return sortedBy { event ->
            when (event) {
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

    private suspend fun dispatchEvent(event: ProjectDiff) {
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

    // NOTE(Dan): Right now we need to use the ProjectDiff serializer in the extension rather than the more specific
    // type they will actually receive. This is simply to make sure that the "type" property is actually added by
    // `kotlinx.serialization`.
    private class Extensions(pluginConfig: ConfigSchema.Plugins.Projects.Simple) {
        private val e = pluginConfig.extensions

        val projectRenamed = optionalExtension(e.all ?: e.projectRenamed, ProjectDiff.serializer(), Unit.serializer())

        val membersAddedToProject = optionalExtension(e.all ?: e.membersAddedToProject, ProjectDiff.serializer(), Unit.serializer())
        val membersRemovedFromProject = optionalExtension(e.all ?: e.membersRemovedFromProject, ProjectDiff.serializer(), Unit.serializer())

        val membersRemovedFromGroup = optionalExtension(e.all ?: e.membersRemovedFromGroup, ProjectDiff.serializer(), Unit.serializer())
        val membersAddedToGroup = optionalExtension(e.all ?: e.membersAddedToGroup, ProjectDiff.serializer(), Unit.serializer())

        val projectArchived = optionalExtension(e.all ?: e.projectArchived, ProjectDiff.serializer(), Unit.serializer())
        val projectUnarchived = optionalExtension(e.all ?: e.projectUnarchived, ProjectDiff.serializer(), Unit.serializer())

        val roleChanged = optionalExtension(e.all ?: e.roleChanged, ProjectDiff.serializer(), Unit.serializer())

        val groupCreated = optionalExtension(e.all ?: e.groupCreated, ProjectDiff.serializer(), Unit.serializer())
        val groupRenamed = optionalExtension(e.all ?: e.groupRenamed, ProjectDiff.serializer(), Unit.serializer())
        val groupDeleted = optionalExtension(e.all ?: e.groupDeleted, ProjectDiff.serializer(), Unit.serializer())
    }

    companion object : Loggable {
        override val log = logger()
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
