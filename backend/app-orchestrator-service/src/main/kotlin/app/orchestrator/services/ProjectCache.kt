package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.project.api.UserStatusResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProjectCache(private val serviceClient: AuthenticatedClient) {
    private data class CacheEntry(val timestamp: Long, val userStatus: UserStatusResponse)
    data class CacheResponse(val isFresh: Boolean, val userStatus: UserStatusResponse?)

    private val cache = HashMap<String, CacheEntry>(0)
    private val mutex = Mutex()

    suspend fun retrieveProjectStatus(username: String, revalidate: Boolean = false): CacheResponse {
        return if (revalidate) {
            CacheResponse(true, updateCache(username))
        } else {
            val existingEntry = mutex.withLock { cache[username] }
            if (existingEntry != null && System.currentTimeMillis() - existingEntry.timestamp <= MAX_AGE) {
                CacheResponse(false, existingEntry.userStatus)
            } else {
                CacheResponse(true, updateCache(username))
            }
        }
    }

    private suspend fun updateCache(
        username: String
    ): UserStatusResponse {
        val result = ProjectMembers.userStatus.call(UserStatusRequest(username), serviceClient).orThrow()
        mutex.withLock {
            cache[username] = CacheEntry(System.currentTimeMillis(), result)
        }
        return result
    }

    companion object {
        private const val MAX_AGE = 1000L * 60 * 5
    }
}

suspend fun ProjectCache.retrieveRole(username: String, project: String): ProjectRole? {
    val status = retrieveProjectStatus(username)
    if (status.isFresh) {
        return status.userStatus?.membership?.find { it.projectId == project }?.whoami?.role
    } else {
        val existingRole = status.userStatus?.membership?.find { it.projectId == project }?.whoami?.role
        if (existingRole != null) return existingRole
        val freshStatus = retrieveProjectStatus(username, true)
        return freshStatus.userStatus?.membership?.find { it.projectId == project }?.whoami?.role
    }
}
