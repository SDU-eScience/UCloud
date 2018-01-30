package dk.sdu.cloud.auth.api

import com.auth0.jwt.impl.PublicClaims
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.service.RawAuthToken
import dk.sdu.cloud.service.TokenValidation

suspend fun TokenValidation.validateAndClaim(
    token: RawAuthToken,
    audience: List<String>? = null,
    cloud: AuthenticatedCloud
): DecodedJWT? {
    val validated = validateOrNull(token, audience) ?: return null
    val jwtId = validated.getClaim(PublicClaims.JWT_ID).asString() ?: return null

    return if (AuthDescriptions.claim.call(ClaimOneTimeToken(jwtId), cloud) is RESTResponse.Ok) {
        validated
    } else {
        null
    }
}

