package dk.sdu.cloud.webdav.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UserClient(
    val client: AuthenticatedClient,
    val homePath: String,
    var lastUse: Long = System.currentTimeMillis()
)

class UserClientFactory(
    private val clientFactory: (refreshToken: String) -> AuthenticatedClient
) {
    // Maps refreshToken to userClient.
    // DO NOT REPLACE THIS WITH USER TO CLIENT! We need to maintain the correct relationship between request and
    // refreshToken.
    private val clientCache = HashMap<String, UserClient>()
    private val mutex = Mutex()
    private var nextClean: Long = 0L

    suspend fun retrieveClient(refreshToken: String): UserClient {
        mutex.withLock {
            val existing = clientCache[refreshToken]
            if (existing != null) {
                existing.lastUse = System.currentTimeMillis()
                return existing
            }
        }

        val client = clientFactory(refreshToken)
        val homePath = FileDescriptions.findHomeFolder.call(FindHomeFolderRequest(""), client).orThrow()
        val userClient = UserClient(client, homePath.path)

        cleanup()

        mutex.withLock {
            return clientCache[refreshToken] ?: userClient.also {
                clientCache[refreshToken] = it
            }
        }
    }

    private suspend fun cleanup() {
        if (System.currentTimeMillis() > nextClean) {
            mutex.withLock {
                val now = System.currentTimeMillis()
                if (now <= nextClean) return
                val iterator = clientCache.iterator()
                while (iterator.hasNext()) {
                    val next = iterator.next()
                    if (now - next.value.lastUse > 1_000 * 60L * 60L) {
                        iterator.remove()
                    }
                }
            }
        }
    }
}
