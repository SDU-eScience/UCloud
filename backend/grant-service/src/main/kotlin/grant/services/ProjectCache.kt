package dk.sdu.cloud.grant.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.project.api.ListAllGroupMembersRequest
import dk.sdu.cloud.project.api.ListSubProjectsRequest
import dk.sdu.cloud.project.api.LookupPrincipalInvestigatorRequest
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectGroups
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.project.api.ViewAncestorsRequest
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.SimpleCache
import io.ktor.http.HttpStatusCode

data class ProjectAndGroup(val project: String, val group: String)

class ProjectCache(private val serviceClient: AuthenticatedClient) {
    val memberStatus = SimpleCache<String, UserStatusResponse> { username ->
        ProjectMembers.userStatus.call(
            UserStatusRequest(username),
            serviceClient
        ).orThrow()
    }

    val groupMembers = SimpleCache<ProjectAndGroup, List<String>> { (project, group) ->
        ProjectGroups.listAllGroupMembers.call(
            ListAllGroupMembersRequest(project, group),
            serviceClient
        ).orThrow()
    }

    val ancestors = SimpleCache<String, List<Project>> { project ->
        Projects.viewAncestors.call(
            ViewAncestorsRequest,
            serviceClient.withProject(project)
        ).orThrow()
    }

    val principalInvestigators = SimpleCache<String, String> { project ->
        Projects.lookupPrincipalInvestigator.call(
            LookupPrincipalInvestigatorRequest,
            serviceClient.withProject(project)
        ).orThrow().principalInvestigator
    }

    val subprojects = SimpleCache<String, List<Project>> { project ->
        Projects.listSubProjects.call(
            ListSubProjectsRequest(PaginationRequest.FULL_READ),
            serviceClient.withProject(project)
        ).orThrow().items
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
