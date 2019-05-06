package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.validateAndDecodeOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthenticationService(
    private val serviceClient: AuthenticatedClient,
    private val tokenValidation: TokenValidation<*>
) {
    private val localTokenCache = HashMap<String, SecurityPrincipalToken>()
    private val mutex = Mutex()

    suspend fun validate(token: String): SecurityPrincipalToken? {
        mutex.withLock {
            val existing = localTokenCache[token]
            if (existing != null && existing.expiresAt > System.currentTimeMillis()) {
                return existing
            }
        }

        when (val resp = AuthDescriptions.refresh.call(
            Unit,
            serviceClient.withoutAuthentication().bearerAuth(token)
        )) {
            is IngoingCallResponse.Error -> {
                return null
            }

            is IngoingCallResponse.Ok -> {
                val validated = tokenValidation.validateAndDecodeOrNull(resp.result.accessToken)
                if (validated != null) {
                    mutex.withLock {
                        localTokenCache[token] = validated
                        return validated
                    }
                }
                return null
            }
        }
    }
}
