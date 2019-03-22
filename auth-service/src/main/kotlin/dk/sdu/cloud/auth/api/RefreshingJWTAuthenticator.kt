package dk.sdu.cloud.auth.api

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.OutgoingCall
import dk.sdu.cloud.calls.client.OutgoingCallCompanion
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.calls.client.outgoingAuthToken
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.net.ConnectException
import java.time.temporal.ChronoUnit
import java.util.*

class RefreshingJWTAuthenticator(
    private val client: RpcClient,
    private val refreshToken: String,
    private val tokenValidation: TokenValidation<DecodedJWT>
) {
    private var currentAccessToken = "~~token will not validate~~"

    suspend fun retrieveTokenRefreshIfNeeded(): String {
        val currentToken = currentAccessToken
        return if (tokenValidation.validateOrNull(currentToken).isExpiringSoon()) {
            refresh()
        } else {
            currentToken
        }
    }

    private fun DecodedJWT?.isExpiringSoon(): Boolean {
        if (this == null) return true
        return Date().toInstant().plus(3, ChronoUnit.MINUTES).isAfter(expiresAt.toInstant())
    }

    private suspend fun refresh(): String {
        log.debug("Refreshing token")
        val validatedToken = tokenValidation.validateOrNull(currentAccessToken)
        if (validatedToken.isExpiringSoon()) {
            val result = client.call(AuthDescriptions.refresh, Unit, OutgoingHttpCall) {
                it.builder.header(HttpHeaders.Authorization, "Bearer $refreshToken")
            }

            if (result is IngoingCallResponse.Ok) {
                currentAccessToken = result.result.accessToken
                return currentAccessToken
            } else {
                if (
                    result.statusCode == HttpStatusCode.BadGateway ||
                    result.statusCode == HttpStatusCode.GatewayTimeout
                ) {
                    throw ConnectException(
                        "Unable to connect to authentication service while trying " +
                                "to refresh access token"
                    )
                }

                if (
                    result.statusCode == HttpStatusCode.Unauthorized ||
                    result.statusCode == HttpStatusCode.Forbidden
                ) {
                    throw IllegalStateException("We are not authorized to refresh the token! $result")
                }

                throw ConnectException("Unexpected status code from auth service while refreshing: $result")
            }
        }
        return currentAccessToken
    }

    fun authenticateClient(
        backend: OutgoingCallCompanion<*>
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
