package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.ViewMemberInProjectRequest
import dk.sdu.cloud.service.Loggable
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
                if (System.currentTimeMillis() > existing.expiry) {
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
                cache[cacheKey] = CacheEntryValue(member, System.currentTimeMillis() + 60_000)
                member
            } else {
                null
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
