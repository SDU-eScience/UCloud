package dk.sdu.cloud.auth.services

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import dk.sdu.cloud.auth.api.*
import java.util.*

class JWTFactory(
    private val jwtAlg: JWTAlgorithm,
    private val serviceLicenseAgreement: ServiceAgreementText? = null,
    private val disable2faCheck: Boolean = false
) : TokenGenerationService {
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
        withClaim("uid", 42)

        withIssuer("cloud.sdu.dk")

        when (user) {
            is Person -> {
                withClaim("firstNames", user.firstNames)
                withClaim("lastName", user.lastName)
                if (user.email != null) withClaim("email", user.email)
                if (user.organizationId != null) withClaim("orgId", user.organizationId)
                withClaim("twoFactorAuthentication", if (!disable2faCheck) user.twoFactorAuthentication else true)
                withClaim(
                    "serviceLicenseAgreement",
                    serviceLicenseAgreement == null || user.serviceLicenseAgreement == serviceLicenseAgreement.version
                )
            }

            is ProviderPrincipal, is ServicePrincipal -> {
                // Do nothing
            }
        }

        val type = when (user) {
            is Person -> {
                if (user.connections.isNotEmpty()) "wayf"
                else "password"
            }
            is ServicePrincipal -> "service"
            is ProviderPrincipal -> "provider"
            else -> error("Unknown principal type: $user")
        }
        withClaim("principalType", type)
    }

    companion object {
        const val CLAIM_EXTENDED_BY = "extendedBy"
        const val CLAIM_SESSION_REFERENCE = "publicSessionReference"
        const val CLAIM_EXTENDED_BY_CHAIN = "extendedByChain"
    }
}
