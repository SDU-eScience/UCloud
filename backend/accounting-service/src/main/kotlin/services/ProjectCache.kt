package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.project.api.*
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.SimpleCache

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

    val subprojects = SimpleCache<String, List<Project>> { project ->
        Projects.listSubProjects.call(
            ListSubProjectsRequest(PaginationRequest.FULL_READ),
            serviceClient.withProject(project)
        ).orThrow().items
    }
}
