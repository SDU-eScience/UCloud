package dk.sdu.cloud.webdav.services

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.ClientAndBackend
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.validateAndDecodeOrNull
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UserClient(
    val client: AuthenticatedClient,
    val homePath: String,
    val username: String,
    var lastUse: Long = System.currentTimeMillis()
)

class UserClientFactory(
    private val clientAndBackend: ClientAndBackend,
    private val tokenValidationJWT: TokenValidationJWT
) {
    // Maps refreshToken to userClient.
    // DO NOT REPLACE THIS WITH USER TO CLIENT! We need to maintain the correct relationship between request and
    // refreshToken.
    private val clientCache = HashMap<String, UserClient>()
    private val mutex = Mutex()
    private var nextClean: Long = 0L

    suspend fun retrieveClient(refreshToken: String): UserClient {
        try {
            mutex.withLock {
                val existing = clientCache[refreshToken]
                if (existing != null) {
                    existing.lastUse = System.currentTimeMillis()
                    return existing
                }
            }

            val authenticator = RefreshingJWTAuthenticator(clientAndBackend.client, refreshToken, tokenValidationJWT)
            val accessToken = authenticator.retrieveTokenRefreshIfNeeded()
            val decodedToken = tokenValidationJWT.validateAndDecodeOrNull(accessToken) ?:
                throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)

            val client = authenticator.authenticateClient(clientAndBackend.backend)
            val homePath = "/home/${decodedToken.principal.username}"
            val userClient = UserClient(client, homePath, decodedToken.principal.username)

            cleanup()

            mutex.withLock {
                return clientCache[refreshToken] ?: userClient.also {
                    clientCache[refreshToken] = it
                }
            }
        } catch (ex: IllegalStateException) {
            throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
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
