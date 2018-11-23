package dk.sdu.cloud.service

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.client.ServiceDescription

class TokenValidationFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)

        val config = ctx.configuration.requestChunkAt<TokenValidationConfig>("tokenValidation")

        @Suppress("UNCHECKED_CAST") val validator = when {
            config.jwt != null -> createJWTValidator(config.jwt)

            else -> {
                throw IllegalArgumentException(
                    "TokenValidationFeature could not find a suitable token " +
                            "validation strategy"
                )
            }
        } as TokenValidation<Any>

        ctx.tokenValidation = validator
    }

    private fun createJWTValidator(config: JWTTokenValidationConfig): TokenValidation<DecodedJWT> {
        return when {
            config.publicCertificate != null -> TokenValidationJWT.withPublicCertificate(config.publicCertificate)
            config.sharedSecret != null -> TokenValidationJWT.withSharedSecret(config.sharedSecret)
            else -> throw IllegalArgumentException("No configuration for JWT found")
        }
    }

    companion object Feature : MicroFeatureFactory<TokenValidationFeature, Unit> {
        internal val tokenValidationKey = MicroAttributeKey<TokenValidation<Any>>("token-validation")

        override val key: MicroAttributeKey<TokenValidationFeature> = MicroAttributeKey("token-validation-feature")
        override fun create(config: Unit): TokenValidationFeature = TokenValidationFeature()
    }
}

var Micro.tokenValidation: TokenValidation<Any>
    get() = attributes[TokenValidationFeature.tokenValidationKey]
    private set (value) {
        attributes[TokenValidationFeature.tokenValidationKey] = value
    }

internal data class TokenValidationConfig(
    val jwt: JWTTokenValidationConfig?
)

internal data class JWTTokenValidationConfig(
    val publicCertificate: String?,
    val sharedSecret: String?
)

