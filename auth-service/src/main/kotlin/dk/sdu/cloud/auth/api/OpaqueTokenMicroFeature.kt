package dk.sdu.cloud.auth.api

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.MicroAttributeKey
import dk.sdu.cloud.service.MicroFeature
import dk.sdu.cloud.service.MicroFeatureFactory
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.TokenValidationFeature
import dk.sdu.cloud.service.orThrow
import dk.sdu.cloud.service.tokenValidation
import kotlinx.coroutines.runBlocking

class OpaqueTokenMicroFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(RefreshingJWTCloudFeature)
        ctx.requireFeature(TokenValidationFeature)

        ctx.tokenValidation.addToChain(0, OpaqueTokenValidation(ctx.refreshingJwtCloud))
    }

    companion object : MicroFeatureFactory<OpaqueTokenMicroFeature, Unit> {
        override val key: MicroAttributeKey<OpaqueTokenMicroFeature> = MicroAttributeKey("opaque-token-feature")
        override fun create(config: Unit): OpaqueTokenMicroFeature = OpaqueTokenMicroFeature()
    }
}

class OpaqueTokenValidation(val cloud: AuthenticatedCloud) : TokenValidation<SecurityPrincipalToken> {
    override val tokenType: Class<SecurityPrincipalToken> = SecurityPrincipalToken::class.java

    override fun decodeToken(token: SecurityPrincipalToken): SecurityPrincipalToken {
        return token
    }

    override fun validate(token: String, scopes: List<SecurityScope>?): SecurityPrincipalToken {
        if (!token.startsWith(OPAQUE_TOKEN_PREFIX)) throw IllegalArgumentException("Invalid token")

        return runBlocking {
            AuthDescriptions.verifyToken.call(VerifyTokenRequest(token), cloud).orThrow()
        }
    }
}

internal const val OPAQUE_TOKEN_PREFIX = "cloud-"
