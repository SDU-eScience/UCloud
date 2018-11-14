package dk.sdu.cloud.auth.services

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AccessTokenContents
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ServicePrincipal
import java.util.*

class JWTFactory(private val jwtAlg: JWTAlgorithm) : TokenGenerationService {
    override fun generate(contents: AccessTokenContents): String {
        val iat = Date(contents.createdAt)
        val exp = contents.expiresAt?.let { Date(it) }

        return JWT.create().run {
            writeStandardClaims(contents.user)
            withExpiresAt(exp)
            withIssuedAt(iat)
            withAudience(*(contents.scopes.map { it.toString() }).toTypedArray())
            if (contents.extendedBy != null) withClaim(CLAIM_EXTENDED_BY, contents.extendedBy)
            if (contents.claimableId != null) withJWTId(contents.claimableId)
            if (contents.sessionReference != null) withClaim(CLAIM_SESSION_REFERENCE, contents.sessionReference)
            sign(jwtAlg)
        }
    }

    fun create(
        user: Principal,
        expiresIn: Long,
        audience: List<SecurityScope>,
        extendedBy: String? = null,
        jwtId: String? = null,
        sessionReference: String? = null
    ): AccessToken {
        val now = System.currentTimeMillis()
        val iat = Date(now)
        val exp = Date(now + expiresIn)

        val token = JWT.create().run {
            writeStandardClaims(user)
            withExpiresAt(exp)
            withIssuedAt(iat)
            withAudience(*(audience.map { it.toString() }).toTypedArray())
            if (extendedBy != null) withClaim(CLAIM_EXTENDED_BY, extendedBy)
            if (jwtId != null) withJWTId(jwtId)
            if (sessionReference != null) withClaim(CLAIM_SESSION_REFERENCE, sessionReference)
            sign(jwtAlg)
        }

        return AccessToken(token)
    }

    private fun JWTCreator.Builder.writeStandardClaims(user: Principal) {
        withSubject(user.id)
        withClaim("role", user.role.name)

        withIssuer("cloud.sdu.dk")

        when (user) {
            is Person -> {
                withClaim("firstNames", user.firstNames)
                withClaim("lastName", user.lastName)
                if (user.orcId != null) withClaim("orcId", user.orcId)
                if (user.title != null) withClaim("title", user.title)
            }

            is ServicePrincipal -> {
                // Do nothing
            }
        }

        // TODO This doesn't seem right
        val type = when (user) {
            is Person.ByWAYF -> "wayf"
            is Person.ByPassword -> "password"
            is ServicePrincipal -> "service"
        }
        withClaim("principalType", type)
    }

    companion object {
        const val CLAIM_EXTENDED_BY = "extendedBy"
        const val CLAIM_SESSION_REFERENCE = "publicSessionReference"
    }
}
