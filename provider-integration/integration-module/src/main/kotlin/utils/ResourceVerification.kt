package dk.sdu.cloud.utils

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.project.api.v2.Project
import dk.sdu.cloud.project.api.v2.Projects
import dk.sdu.cloud.project.api.v2.ProjectsRetrieveRequest
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.Resource
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.service.SimpleCache

/**
 * Used to verify that a user has access to a particular resource. This is particularly useful in IPC handlers.
 *
 * This utility service is only available in Server mode.
 */
object ResourceVerification {
    lateinit var client: AuthenticatedClient

    suspend fun verifyAccessToResource(
        ucloudUsername: String?,
        resource: Resource<*, *>,
        allowRetry: Boolean = true
    ) {
        verifyAccessToResource(
            ucloudUsername,
            resource.owner.createdBy,
            resource.owner.project,
            resource.permissions?.others ?: emptyList(),
            projectMembershipIsSufficient = false,
            allowRetry
        )
    }

    suspend fun verifyAccessToResource(
        ucloudUsername: String?,
        createdBy: String,
        project: String?,
        acl: List<ResourceAclEntry>,
        projectMembershipIsSufficient: Boolean = true,
        allowRetry: Boolean = true
    ) {
        if (ucloudUsername == null) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        if (project != null) {
            try {
                val resolvedProject = projectCache.get(project)
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                val members = (resolvedProject.status.members ?: emptyList())
                val groups = (resolvedProject.status.groups ?: emptyList())

                val myself = members.find { it.username == ucloudUsername }
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

                if (!myself.role.isAdmin() && !projectMembershipIsSufficient) {
                    var foundAclEntry = false
                    for (entry in acl) {
                        if (entry.permissions.isEmpty()) continue
                        val hasPermissions = when (val entity = entry.entity) {
                            is AclEntity.ProjectGroup -> {
                                groups.any { group ->
                                    group.id == entity.group &&
                                            group.specification.project == entity.projectId &&
                                            (group.status.members ?: emptyList()).contains(ucloudUsername)
                                }
                            }

                            is AclEntity.User -> ucloudUsername == entity.username
                        }

                        if (hasPermissions) {
                            foundAclEntry = true
                            break
                        }
                    }

                    if (!foundAclEntry) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                }
            } catch (ex: RPCException) {
                if (allowRetry) {
                    projectCache.remove(project)
                    return verifyAccessToResource(ucloudUsername, createdBy, project, acl, allowRetry = false)
                } else {
                    throw ex
                }
            }
        } else {
            if (createdBy != ucloudUsername) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }
        }
    }

    private val projectCache = SimpleCache<String, Project>(
        lookup = { projectId ->
            Projects.retrieve.call(
                ProjectsRetrieveRequest(
                    projectId,
                    includeGroups = true,
                    includeMembers = true,
                ),
                client
            ).orNull()
        }
    )
}
