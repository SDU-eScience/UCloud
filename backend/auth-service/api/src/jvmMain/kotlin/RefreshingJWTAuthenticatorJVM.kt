package dk.sdu.cloud.auth.api

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.calls.client.OutgoingCall
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.TokenValidation
import java.time.temporal.ChronoUnit
import java.util.*

private val jwtDecoder = JWT()

private fun DecodedJWT?.isExpiringSoon(): Boolean {
    if (this == null) return true
    return Date(Time.now()).toInstant().plus(3, ChronoUnit.MINUTES).isAfter(expiresAt.toInstant())
}

@Deprecated(
    "Should be replaced with RefreshingJWTAuthenticator(client, JwtRefresher.Normal(refreshToken))",
    replaceWith = ReplaceWith("RefreshingJWTAuthenticator(client, JwtRefresher.Normal(refreshToken))")
)
fun RefreshingJWTAuthenticator(
    client: RpcClient,
    refreshToken: String,
    @Suppress("UNUSED_PARAMETER") tokenValidation: TokenValidation<DecodedJWT>,
): RefreshingJWTAuthenticator {
    return RefreshingJWTAuthenticator(client, JwtRefresher.Normal(refreshToken, OutgoingHttpCall))
}

fun RefreshingJWTAuthenticator(
    client: RpcClient,
    refresher: JwtRefresher,
): RefreshingJWTAuthenticator {
    return RefreshingJWTAuthenticator(client, refresher, becomesInvalidSoon = { accessToken ->
        runCatching { jwtDecoder.decodeJwt(accessToken) }.getOrNull().isExpiringSoon()
    })
}
