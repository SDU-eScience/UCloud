package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.project.api.ListSubProjectsRequest
import dk.sdu.cloud.project.api.Project
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.project.api.UserStatusResponse
import dk.sdu.cloud.project.api.ViewAncestorsRequest
import dk.sdu.cloud.project.api.ViewMemberInProjectRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProjectCache(private val serviceClient: AuthenticatedClient) {
    private data class CacheEntryKey(val projectId: String, val username: String)
    private data class CacheEntryValue(val member: ProjectMember, val expiry: Long)

    private val cache = HashMap<CacheEntryKey, CacheEntryValue>()
    private val mutex = Mutex()

    suspend fun viewMember(projectId: String, username: String): ProjectMember? {
        mutex.withLock {
            val cacheKey = CacheEntryKey(projectId, username)
            val existing = cache[cacheKey]
            if (existing != null) {
                if (Time.now() > existing.expiry) {
                    cache.remove(cacheKey)
                } else {
                    return existing.member
                }
            }

            val member = Projects.viewMemberInProject.call(
                ViewMemberInProjectRequest(projectId, username),
                serviceClient
            ).orNull()?.member

            log.debug("viewMember($projectId, $username) = $member")

            return if (member != null) {
                cache[cacheKey] = CacheEntryValue(member, Time.now() + 60_000)
                member
            } else {
                null
            }
        }
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

    val memberStatus = SimpleCache<String, UserStatusResponse> { username ->
        ProjectMembers.userStatus.call(
            UserStatusRequest(username),
            serviceClient
        ).orThrow()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
