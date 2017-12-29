package dk.sdu.cloud.auth.api

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.client.prepare
import dk.sdu.cloud.service.TokenValidation
import kotlinx.coroutines.experimental.runBlocking
import org.asynchttpclient.BoundRequestBuilder
import java.net.ConnectException

class RefreshingJWTAuthenticator(
        override val parent: CloudContext,
        refreshToken: String
) : AuthenticatedCloud {
    private val lock = Any()
    private var currentAccessToken = "~~token will not validate~~"
    private val refreshAuthenticator = RefreshTokenAuthenticator(parent, refreshToken)

    fun retrieveTokenRefreshIfNeeded(): String {
        val tempToken = currentAccessToken
        return if (TokenValidation.validateOrNull(tempToken) == null) {
            refresh()
        } else {
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
        synchronized(lock) {
            if (TokenValidation.validateOrNull(currentAccessToken) == null) {
                val prepared = AuthDescriptions.refresh.prepare()
                val result = runBlocking {
                    prepared.call(refreshAuthenticator)
                }

                if (result is RESTResponse.Ok) {
                    currentAccessToken = result.result.accessToken
                    return currentAccessToken
                } else {
                    throw ConnectException("Unable to connect to authentication service while trying " +
                            "to refresh access token")
                }
            }
            return currentAccessToken
        }
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