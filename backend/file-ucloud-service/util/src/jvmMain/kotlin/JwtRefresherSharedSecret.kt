package dk.sdu.cloud.file.ucloud.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.service.Time
import java.util.*

class JwtRefresherSharedSecret(val sharedSecret: String) : JwtRefresher() {
    private val alg = Algorithm.HMAC512(sharedSecret)
    override suspend fun fetchToken(client: RpcClient): String {
        val iat = Date(Time.now())
        val exp = Date(Time.now() + (1000 * 60L * 15))

        return JWT.create().run {
            withSubject("_ucloud")
            withClaim("role", "SERVICE")
            withClaim("uid", 0)

            withIssuer("cloud.sdu.dk")
            withClaim("principalType", "service")
            withExpiresAt(exp)
            withIssuedAt(iat)
            withAudience("all:write")
            withArrayClaim(CLAIM_EXTENDED_BY_CHAIN, emptyArray<String>())
            sign(alg)
        }
    }

    companion object {
        const val CLAIM_EXTENDED_BY = "extendedBy"
        const val CLAIM_SESSION_REFERENCE = "publicSessionReference"
        const val CLAIM_EXTENDED_BY_CHAIN = "extendedByChain"
    }
}