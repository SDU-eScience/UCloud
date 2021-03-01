package dk.sdu.cloud.auth.api

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.service.Loggable
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay

sealed class JwtRefresher {
    abstract suspend fun fetchToken(client: RpcClient): String

    class Normal(private val refreshToken: String) : JwtRefresher() {
        override suspend fun fetchToken(client: RpcClient): String {
            return client.call(AuthDescriptions.refresh, Unit, OutgoingHttpCall) {
                it.builder.header(HttpHeaders.Authorization, "Bearer $refreshToken")
            }.orThrow().accessToken
        }
    }

    class Provider(private val refreshToken: String) : JwtRefresher() {
        override suspend fun fetchToken(client: RpcClient): String {
            return client
                .call(
                    AuthProviders.refresh,
                    bulkRequestOf(RefreshToken(refreshToken)),
                    OutgoingHttpCall
                )
                .orThrow().responses
                .single().accessToken
        }
    }

    class ProviderOrchestrator(
        private val authenticatedClient: AuthenticatedClient,
        private val providerId: String,
    ) : JwtRefresher() {
        override suspend fun fetchToken(client: RpcClient): String {
            return AuthProviders.refreshAsProvider
                .call(
                    bulkRequestOf(AuthProvidersRefreshAsProviderRequestItem(providerId)),
                    authenticatedClient
                ).orThrow().responses
                .single().accessToken
        }
    }
}

class RefreshingJWTAuthenticator(
    private val client: RpcClient,
    private val refresher: JwtRefresher,
    private val becomesInvalidSoon: (accessToken: String) -> Boolean,
) {
    private var currentAccessToken = "~~token will not validate~~"

    suspend fun retrieveTokenRefreshIfNeeded(): String {
        val currentToken = currentAccessToken
        return if (becomesInvalidSoon(currentToken)) {
            refresh()
        } else {
            currentToken
        }
    }

    private suspend fun refresh(attempts: Int = 0): String {
        log.trace("Refreshing token")
        if (becomesInvalidSoon(currentAccessToken)) {
            try {
                currentAccessToken = refresher.fetchToken(client)
                return currentAccessToken
            } catch (ex: RPCException) {
                val statusCode = ex.httpStatusCode
                if (
                    statusCode == HttpStatusCode.BadGateway ||
                    statusCode == HttpStatusCode.GatewayTimeout
                ) {
                    throw RPCException(
                        "Unable to connect to authentication service while trying " +
                            "to refresh access token",
                        HttpStatusCode.BadGateway
                    )
                }

                if (
                    statusCode == HttpStatusCode.Unauthorized ||
                    statusCode == HttpStatusCode.Forbidden
                ) {
                    throw RPCException("We are not authorized to refresh the token", statusCode)
                }

                if (statusCode == HttpStatusCode.NotFound && attempts < 5) {
                    // Deals with a bug when running this in dev mode via Launcher
                    // Some times we request an authorization token before the auth service is actually responding

                    delay(1000 * 5)
                    return refresh(attempts + 1)
                }

                throw RPCException(
                    "Unexpected status code from auth service while refreshing: $statusCode",
                    statusCode
                )
            }
        }
        return currentAccessToken
    }

    fun authenticateClient(
        backend: OutgoingCallCompanion<*>,
    ): AuthenticatedClient {
        return AuthenticatedClient(client, backend) { authenticateCall(it) }
    }

    suspend fun authenticateCall(ctx: OutgoingCall) {
        val token = retrieveTokenRefreshIfNeeded()
        ctx.attributes.outgoingAuthToken = token
    }

    companion object : Loggable {
        override val log = logger()
    }
}
