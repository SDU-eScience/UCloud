package dk.sdu.cloud.auth.api

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.prepare
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.net.ConnectException
import java.time.temporal.ChronoUnit
import java.util.*

class RefreshingJWTAuthenticator(
    cloud: CloudContext,
    refreshToken: String,
    private val tokenValidation: TokenValidation<DecodedJWT>
) {
    private val lock = Any()
    private var currentAccessToken = "~~token will not validate~~"
    private val refreshAuthenticator = RefreshTokenAuthenticator(cloud, refreshToken)

    fun retrieveTokenRefreshIfNeeded(): String {
        log.debug("retrieveTokenRefreshIfNeeded()")
        val currentToken = currentAccessToken
        return if (tokenValidation.validateOrNull(currentToken).isExpiringSoon()) {
            log.debug("Refreshing token")
            refresh()
        } else {
            log.debug("Using currentToken: $currentToken")
            currentToken
        }
    }

    private fun DecodedJWT?.isExpiringSoon(): Boolean {
        if (this == null) return true
        return expiresAt.toInstant().isAfter(Date().toInstant().plus(3, ChronoUnit.MINUTES))
    }

    private fun refresh(): String {
        // The initial check (in retrieveTokenRefreshIfNeeded) is done without locking. This way multiple threads
        // might decide that a refresh is needed. Because of this we check once we acquire the lock to see if someone
        // had already done the token refresh. It is _a lot_ cheaper to do the check than to refresh again, also
        // avoids some load on the auth service.
        log.debug("Entering refresh() - Awaiting lock")
        synchronized(lock) {
            val validatedToken = tokenValidation.validateOrNull(currentAccessToken)
            if (validatedToken.isExpiringSoon()) {
                log.info("Need to refresh token")
                val prepared = AuthDescriptions.refresh.prepare()
                val result = runBlocking {
                    prepared.call(refreshAuthenticator)
                }

                log.info("Refresh token result: $result")
                if (result is RESTResponse.Ok) {
                    currentAccessToken = result.result.accessToken
                    return currentAccessToken
                } else {
                    if (
                        result.status == HttpStatusCode.BadGateway.value ||
                        result.status == HttpStatusCode.GatewayTimeout.value
                    ) {
                        throw ConnectException(
                            "Unable to connect to authentication service while trying " +
                                    "to refresh access token"
                        )
                    }

                    if (
                        result.status == HttpStatusCode.Unauthorized.value ||
                        result.status == HttpStatusCode.Forbidden.value
                    ) {
                        throw IllegalStateException("We are not authorized to refresh the token! $result")
                    }

                    throw ConnectException("Unexpected status code from auth service while refreshing: $result")
                }
            }
            log.debug("Token already refreshed")
            return currentAccessToken
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

class RefreshingJWTAuthenticatedCloud(
    override val parent: CloudContext,
    refreshToken: String,
    tokenValidation: TokenValidation<DecodedJWT>
) : AuthenticatedCloud {
    val tokenRefresher = RefreshingJWTAuthenticator(parent, refreshToken, tokenValidation)

    override fun HttpRequestBuilder.configureCall() {
        val actualToken = tokenRefresher.retrieveTokenRefreshIfNeeded()
        header("Authorization", "Bearer $actualToken")
    }
}

private class RefreshTokenAuthenticator(
    override val parent: CloudContext,
    private val refreshToken: String
) : AuthenticatedCloud {
    override fun HttpRequestBuilder.configureCall() {
        header("Authorization", "Bearer $refreshToken")
    }
}
