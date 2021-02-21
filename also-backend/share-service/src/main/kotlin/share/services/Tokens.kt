package dk.sdu.cloud.share.services

import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withoutAuthentication

suspend fun createToken(client: AuthenticatedClient, accessToken: String, rights: List<SecurityScope>): String {
    return AuthDescriptions.tokenExtension.call(
        TokenExtensionRequest(
            accessToken,
            rights.map { it.toString() },
            expiresIn = 1000L * 60,
            allowRefreshes = true
        ),
        client
    ).orThrow().refreshToken ?: throw ShareException.InternalError(
        "bad response from token extension. refreshToken == null"
    )
}

suspend fun revokeToken(client: AuthenticatedClient, token: String?) {
    if (token.isNullOrEmpty()) return

    AuthDescriptions.logout.call(
        Unit,
        client.withoutAuthentication().bearerAuth(token)
    )
}

