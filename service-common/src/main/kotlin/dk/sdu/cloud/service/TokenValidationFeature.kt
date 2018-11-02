package dk.sdu.cloud.service

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.client.ServiceDescription

class TokenValidationFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)

        val config = ctx.configuration.requestChunkAt<TokenValidationConfig>("tokenValidation")

        val validator: TokenValidation<*> = when {
            config.jwt != null -> createJWTValidator(config.jwt)

            else -> {
                throw IllegalArgumentException(
                    "TokenValidationFeature could not find a suitable token " +
                            "validation strategy"
                )
            }
        }

        @Suppress("UNCHECKED_CAST")
        ctx.tokenValidation = validator as TokenValidation<Any>
    }

    private fun createJWTValidator(config: JWTTokenValidationConfig): TokenValidation<DecodedJWT> {
        return TokenValidationJWT(config.publicCertificate)
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
    val publicCertificate: String
)
