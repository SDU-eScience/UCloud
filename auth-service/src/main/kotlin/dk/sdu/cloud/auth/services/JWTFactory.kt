package dk.sdu.cloud.auth.services

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ProjectProxy
import dk.sdu.cloud.auth.api.ServicePrincipal
import java.util.*

class JWTFactory(private val jwtAlg: JWTAlgorithm) : TokenGenerationService {
    override fun generate(contents: AccessTokenContents): String {
        val iat = Date(contents.createdAt)
        val exp = Date(contents.expiresAt)

        return JWT.create().run {
            writeStandardClaims(contents.user)
            withExpiresAt(exp)
            withIssuedAt(iat)
            withAudience(*(contents.scopes.map { it.toString() }).toTypedArray())
            if (contents.extendedBy != null) withClaim(CLAIM_EXTENDED_BY, contents.extendedBy)
            if (contents.claimableId != null) withJWTId(contents.claimableId)
            if (contents.sessionReference != null) withClaim(CLAIM_SESSION_REFERENCE, contents.sessionReference)
            withArrayClaim(CLAIM_EXTENDED_BY_CHAIN, contents.extendedByChain.toTypedArray())
            sign(jwtAlg)
        }
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
            is ProjectProxy -> "project"
        }
        withClaim("principalType", type)
    }

    companion object {
        const val CLAIM_EXTENDED_BY = "extendedBy"
        const val CLAIM_SESSION_REFERENCE = "publicSessionReference"
        const val CLAIM_EXTENDED_BY_CHAIN = "extendedByChain"
    }
}
