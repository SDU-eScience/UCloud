package dk.sdu.cloud.plugins

import dk.sdu.cloud.project.api.v2.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class SimpleProjectPlugin : ProjectPlugin {
    // NOTE(Dan, Brian): This plugin is responsible for taking the raw updates, coming in from UCloud/Core, and
    // translating them into a diff. This diff is then passed on two operator defined extensions which are responsible
    // for acting on these changes.
    override suspend fun PluginContext.onProjectUpdated(project: Project) {
        println("Project updated ${project}")
        val oldProject: Project = TODO("Find old project")
        val newProject = project

        val diff = calculateDiff(oldProject, newProject)
        // TODO Call extensions per diff
    }

    private fun calculateDiff(oldProject: Project, newProject: Project): List<ProjectDiff> {
        val result = ArrayList<ProjectDiff>()

        val oldTitle = oldProject.specification.title
        val newTitle = newProject.specification.title

        val oldMembers = oldProject.status.members!!
        val newMembers = newProject.status.members!!

        val oldGroups = oldProject.status.groups!!
        val newGroups = newProject.status.groups!!

        val oldArchived = oldProject.status.archived
        val newArchived = newProject.status.archived

        if (oldTitle != newTitle) {
            result.add(ProjectDiff.ProjectRenamed(oldProject, newProject, newTitle))
        }
        
        if (oldArchived != newArchived) {
            if (newArchived) {
                result.add(ProjectDiff.ProjectArchived(oldProject, newProject))
            } else {
                result.add(ProjectDiff.ProjectUnarchived(oldProject, newProject))
            }
        }

        run {
            val membersSeen = HashSet<String>()
            val addedMembers = ArrayList<ProjectMember>()
            val removedMembers = ArrayList<ProjectMember>()

            for (member in newMembers) {
                val oldStatus = oldMembers.find { it.username == member.username }
                if (oldStatus == null) {
                    addedMembers.add(member)
                } else if (oldStatus.role != member.role) {
                    // We have a new role
                    result.add(ProjectDiff.RoleChanged(oldProject, newProject, member, oldStatus.role, member.role))
                } else {
                    // Nothing has changed
                }

                membersSeen.add(member.username)
            }

            for (member in oldMembers) {
                if (member.username !in membersSeen) {
                    removedMembers.add(member)
                }
            }

            if (addedMembers.isNotEmpty()) {
                result.add(ProjectDiff.MembersAddedToProject(oldProject, newProject, addedMembers))
            }

            if (removedMembers.isNotEmpty()) {
                result.add(ProjectDiff.MembersRemovedFromProject(oldProject, newProject, removedMembers))
            }
        }

        run {
            val groupsSeen = HashSet<String>()
            val addedGroups = ArrayList<Group>()
            val removedGroups = ArrayList<Group>()

            for (group in newGroups) {
                val oldStatus = oldGroups.find { it.id == group.id }

                if (oldStatus != null && oldStatus.specification.title != group.specification.title) {
                    result.add(ProjectDiff.GroupRenamed(oldProject, newProject, group))
                }

                if (oldStatus == null) {
                    addedGroups.add(group)
                    result.add(ProjectDiff.MembersAddedToGroup(oldProject, newProject, group.id, group.status.members!!))
                } else {
                    val addedMembers = ArrayList<String>()
                    val removedMembers = ArrayList<String>()

                    val oldGroupMembers = oldStatus.status.members!!
                    val newGroupMembers = group.status.members!!

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
                        result.add(ProjectDiff.MembersAddedToGroup(oldProject, newProject, group.id, addedMembers))
                    }

                    if (removedMembers.isNotEmpty()) {
                        result.add(ProjectDiff.MembersRemovedFromGroup(oldProject, newProject, group.id, removedMembers))
                    }
                }

                groupsSeen.add(group.id)
            }

            for (group in oldGroups) {
                if (group.id !in groupsSeen) {
                    removedGroups.add(group)
                }
            }

            if (addedGroups.isNotEmpty()) {
                result.add(ProjectDiff.GroupsCreated(oldProject, newProject, addedGroups))
            }

            if (removedGroups.isNotEmpty()) {
                result.add(ProjectDiff.GroupsDeleted(oldProject, newProject, removedGroups))
            }
        }

        return result
    }

    private companion object Extensions {
        val projectRenamed = extension<ProjectDiff.ProjectRenamed, Unit>()

        val membersAddedToProject = extension<ProjectDiff.MembersAddedToProject, Unit>()
        val membersRemovedFromProject = extension<ProjectDiff.MembersRemovedFromProject, Unit>()

        val membersRemovedFromGroup = extension<ProjectDiff.MembersRemovedFromGroup, Unit>()
        val membersAddedToGroup = extension<ProjectDiff.MembersAddedToGroup, Unit>()

        val projectArchived = extension<ProjectDiff.ProjectArchived, Unit>()
        val projectUnarchived = extension<ProjectDiff.ProjectArchived, Unit>()

        val roleChanged = extension<ProjectDiff.RoleChanged, Unit>()

        val groupCreated = extension<ProjectDiff.GroupsCreated, Unit>()
        val groupRenamed = extension<ProjectDiff.GroupRenamed, Unit>()
        val groupDeleted = extension<ProjectDiff.GroupsDeleted, Unit>()
    }

    // NOTE(Dan, Brian): Maps a project ID to a UNIX group ID.
    // The group IDs are allocated by this function, if a mapping does not exist. After which the mapping will
    // be used for eternity. The group IDs are allocated starting at a number specified in the configuration.
    private suspend fun projectIdToGroupId(projectId: String): String {
        TODO()
    }

    // NOTE(Dan): See `projectIdToGroupId()`
    private suspend fun groupIdToGroupId(groupId: String): String {
        TODO()
    }

    /*
    "projects": {
        "simple": {
            "groupIdNamespace": 12267890,
            "extensions": {
                "groupCreated": "/opt/ucloud/example-extensions/project-extension",
                "groupDeleted": "/opt/ucloud/example-extensions/project-extension",
                "memberCreated": "/opt/ucloud/example-extensions/project-extension",
                "memberDeleted": "/opt/ucloud/example-extensions/project-extension"
            }
        }
    }
     */
}

@Serializable
private sealed class ProjectDiff {
    abstract val oldProject: Project
    abstract val newProject: Project

    @Serializable
    @SerialName("project_renamed")
    data class ProjectRenamed(
        override val oldProject: Project,
        override val newProject: Project,
        val newTitle: String
    ) : ProjectDiff()

    @Serializable
    @SerialName("members_added_to_project")
    data class MembersAddedToProject(
        override val oldProject: Project,
        override val newProject: Project,
        val newMembers: List<ProjectMember>,
    ) : ProjectDiff()

    @Serializable
    @SerialName("members_removed_from_project")
    data class MembersRemovedFromProject(
        override val oldProject: Project,
        override val newProject: Project,
        val removedMembers: List<ProjectMember>
    ) : ProjectDiff()

    @Serializable
    @SerialName("members_added_to_group")
    data class MembersAddedToGroup(
        override val oldProject: Project,
        override val newProject: Project,
        val groupId: String,
        val newMembers: List<String>
    ) : ProjectDiff()

    @Serializable
    @SerialName("members_removed_from_group")
    data class MembersRemovedFromGroup(
        override val oldProject: Project,
        override val newProject: Project,
        val groupId: String,
        val removedMembers: List<String>
    ) : ProjectDiff()

    @Serializable
    @SerialName("project_archived")
    data class ProjectArchived(
        override val oldProject: Project,
        override val newProject: Project,
    ) : ProjectDiff()

    @Serializable
    @SerialName("project_unarchived")
    data class ProjectUnarchived(
        override val oldProject: Project,
        override val newProject: Project,
    ) : ProjectDiff()

    @Serializable
    @SerialName("role_changed")
    data class RoleChanged(
        override val oldProject: Project,
        override val newProject: Project,
        val member: ProjectMember,
        val oldRole: ProjectRole,
        val newRole: ProjectRole
    ) : ProjectDiff()

    @Serializable
    @SerialName("group_created")
    data class GroupsCreated(
        override val oldProject: Project,
        override val newProject: Project,
        val groups: List<Group>
    ) : ProjectDiff()

    @Serializable
    @SerialName("group_deleted")
    data class GroupsDeleted(
        override val oldProject: Project,
        override val newProject: Project,
        val groups: List<Group>
    ) : ProjectDiff()

    @Serializable
    @SerialName("group_renamed")
    data class GroupRenamed(
        override val oldProject: Project,
        override val newProject: Project,
        val group: Group,
    ) : ProjectDiff()
}
