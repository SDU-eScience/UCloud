package dk.sdu.cloud.auth.api

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.prepare
import dk.sdu.cloud.service.TokenValidation
import io.ktor.html.insert
import kotlinx.coroutines.experimental.runBlocking
import org.asynchttpclient.BoundRequestBuilder
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount
import java.time.temporal.TemporalUnit
import java.util.*
import java.util.concurrent.TimeUnit

class RefreshingJWTAuthenticator(
    override val parent: CloudContext,
    refreshToken: String
) : AuthenticatedCloud {
    private val lock = Any()
    private var currentAccessToken = "~~token will not validate~~"
    private val refreshAuthenticator = RefreshTokenAuthenticator(parent, refreshToken)

    fun retrieveTokenRefreshIfNeeded(): String {
        log.debug("retrieveTokenRefreshIfNeeded()")
        val tempToken = currentAccessToken
        return if (TokenValidation.validateOrNull(tempToken) == null) {
            log.debug("Refreshing token")
            refresh()
        } else {
            log.debug("Using currentToken: $tempToken")
            tempToken
        }
    }

    override fun BoundRequestBuilder.configureCall() {
        val actualToken = retrieveTokenRefreshIfNeeded()
        setHeader("Authorization", "Bearer $actualToken")
    }

    private fun refresh(): String {
        // The initial check is done without locking. This way multiple threads might decide that a refresh is needed.
        // Because of this we check once we acquire the lock to see if someone had already done the token refresh.
        // It is _a lot_ cheaper to do the check than to refresh again, also avoids some load on the auth service.
        log.debug("Entering refresh() - Awaiting lock")
        synchronized(lock) {
            val validatedToken = TokenValidation.validateOrNull(currentAccessToken)
            if (
                validatedToken == null ||
                validatedToken.expiresAt.toInstant().isAfter((Date().toInstant().plus(5, ChronoUnit.MINUTES)))
            ) {
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
                    throw ConnectException(
                        "Unable to connect to authentication service while trying " +
                                "to refresh access token"
                    )
                }
            }
            log.debug("Token already refreshed")
            return currentAccessToken
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RefreshingJWTAuthenticator::class.java)
    }
}

private class RefreshTokenAuthenticator(
    override val parent: CloudContext,
    private val refreshToken: String
) : AuthenticatedCloud {
    override fun BoundRequestBuilder.configureCall() {
        setHeader("Authorization", "Bearer $refreshToken")
    }
}