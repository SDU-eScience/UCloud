package dk.sdu.cloud.auth.api

import com.auth0.jwt.impl.PublicClaims
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.service.TokenValidation

suspend fun TokenValidation<DecodedJWT>.validateAndClaim(
    token: String,
    audience: List<SecurityScope>? = null,
    client: AuthenticatedClient
): DecodedJWT? {
    val validated = validateOrNull(token, audience) ?: return null
    val jwtId = validated.getClaim(PublicClaims.JWT_ID).asString() ?: return null

    return if (AuthDescriptions.claim.call(ClaimOneTimeToken(jwtId), client) is IngoingCallResponse.Ok) {
        validated
    } else {
        null
    }
}

