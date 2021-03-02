package dk.sdu.cloud.micro

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.InternalTokenValidationJWT
import dk.sdu.cloud.service.TokenValidation

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
            config.publicCertificate != null -> InternalTokenValidationJWT.withPublicCertificate(
                config.publicCertificate
            )
            config.sharedSecret != null -> InternalTokenValidationJWT.withSharedSecret(config.sharedSecret)
            else -> throw IllegalArgumentException("No configuration for JWT found")
        }
    }

    companion object Feature : MicroFeatureFactory<TokenValidationFeature, Unit> {
        internal val tokenValidationKey = MicroAttributeKey<TokenValidation<Any>>("token-validation")

        override val key: MicroAttributeKey<TokenValidationFeature> =
            MicroAttributeKey("token-validation-feature")

        override fun create(config: Unit): TokenValidationFeature =
            TokenValidationFeature()
    }
}


internal data class TokenValidationConfig(
    val jwt: JWTTokenValidationConfig?
)

internal data class JWTTokenValidationConfig(
    val publicCertificate: String?,
    val sharedSecret: String?
)

