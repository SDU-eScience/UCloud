package dk.sdu.cloud.grant.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.Cache
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.SimpleCache
import io.ktor.http.HttpStatusCode

data class ProjectAndGroup(val project: String, val group: String)

class ProjectCache(private val serviceClient: AuthenticatedClient) {
    val memberStatus: Cache<String, UserStatusResponse> = SimpleCache<String, UserStatusResponse> { username ->
        ProjectMembers.userStatus.call(
            UserStatusRequest(username),
            serviceClient
        ).orThrow()
    }

    val groupMembers: Cache<ProjectAndGroup, List<String>> = SimpleCache<ProjectAndGroup, List<String>> { (project, group) ->
        ProjectGroups.listAllGroupMembers.call(
            ListAllGroupMembersRequest(project, group),
            serviceClient
        ).orThrow()
    }

    val ancestors: Cache<String, List<Project>> = SimpleCache<String, List<Project>> { project ->
        Projects.viewAncestors.call(
            ViewAncestorsRequest,
            serviceClient.withProject(project)
        ).orNull()
    }

    val principalInvestigators: Cache<String, String> = SimpleCache<String, String> { project ->
        Projects.lookupPrincipalInvestigator.call(
            LookupPrincipalInvestigatorRequest,
            serviceClient.withProject(project)
        ).orThrow().principalInvestigator
    }

    val subprojects: Cache<String, List<Project>> = SimpleCache<String, List<Project>> { project ->
        Projects.listSubProjects.call(
            ListSubProjectsRequest(PaginationRequest.FULL_READ),
            serviceClient.withProject(project)
        ).orThrow().items
    }

    val admins: Cache<String, List<ProjectMember>> = SimpleCache<String, List<ProjectMember>> { project ->
        ProjectMembers.lookupAdmins.call(
            LookupAdminsRequest(project),
            serviceClient
        ).orThrow().admins
    }
}

suspend fun ProjectCache.isAdminOfParent(projectId: String, actor: Actor): Boolean {
    val ancestors = ancestors.get(projectId) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    val parent = ancestors.getOrNull(ancestors.size - 1)
    if (actor == Actor.System) return true
    if (parent == null && actor is Actor.User && actor.principal.role in Roles.PRIVILEGED) return true
    if (parent == null) return false
    val status =
        memberStatus.get(actor.username) ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)

    return status.membership.any { it.projectId == parent.id && it.whoami.role.isAdmin() }
}

suspend fun ProjectCache.isAdminOfProject(projectId: String, actor: Actor): Boolean {
    if (actor == Actor.System) return true
    val status =
        memberStatus.get(actor.username) ?: throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)

    return status.membership.any { it.projectId == projectId && it.whoami.role.isAdmin() }
}
