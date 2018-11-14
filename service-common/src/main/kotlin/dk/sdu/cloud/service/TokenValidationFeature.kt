package dk.sdu.cloud.service

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.client.ServiceDescription
import io.ktor.http.HttpStatusCode
import java.util.*

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

        val chain = TokenValidationChain()
        chain.addToChain(100, validator)

        @Suppress("UNCHECKED_CAST")
        ctx.tokenValidation = chain
    }

    private fun createJWTValidator(config: JWTTokenValidationConfig): TokenValidation<DecodedJWT> {
        return when {
            config.publicCertificate != null -> TokenValidationJWT.withPublicCertificate(config.publicCertificate)
            config.sharedSecret != null -> TokenValidationJWT.withSharedSecret(config.sharedSecret)
            else -> throw IllegalArgumentException("No configuration for JWT found")
        }
    }

    companion object Feature : MicroFeatureFactory<TokenValidationFeature, Unit> {
        internal val tokenValidationKey = MicroAttributeKey<TokenValidationChain>("token-validation")

        override val key: MicroAttributeKey<TokenValidationFeature> = MicroAttributeKey("token-validation-feature")
        override fun create(config: Unit): TokenValidationFeature = TokenValidationFeature()
    }
}

var Micro.tokenValidation: TokenValidationChain
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

class TokenValidationChain : TokenValidation<Any> {
    override val tokenType: Class<Any> = Any::class.java
    private val chain = TreeMap<Int, TokenValidation<Any>>()

    fun <T : Any> addToChain(weight: Int, validator: TokenValidation<T>) {
        val id = run {
            var guess = weight
            while (guess in chain) {
                guess++
            }
            guess
        }

        @Suppress("UNCHECKED_CAST")
        chain[id] = validator as TokenValidation<Any>
    }

    inline fun <reified T : TokenValidation<*>> findInstanceInChain(): T? {
        return findInstanceInChain(T::class.java)
    }

    fun <T : TokenValidation<*>> findInstanceInChain(type: Class<T>): T? {
        val instance = chain.values.find { type.isInstance(it) }

        @Suppress("UNCHECKED_CAST")
        return if (instance == null) null
        else instance as T
    }

    override fun decodeToken(token: Any): SecurityPrincipalToken {
        val exceptions = ArrayList<Throwable>()
        for ((_, validator) in chain) {
            if (validator.tokenType.isAssignableFrom(token.javaClass)) {
                val result = runCatching { validator.decodeToken(token) }
                if (result.isSuccess) {
                    return result.getOrThrow()
                } else {
                    result.exceptionOrNull()?.let { exceptions.add(it) }
                }
            }
        }

        if (log.isDebugEnabled) {
            log.debug("Unable to decode token $token")
            log.debug("Stack traces follows:")
            exceptions.forEach { log.debug(it.stackTraceToString()) }
        }

        throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
    }

    override fun validate(token: String, scopes: List<SecurityScope>?): Any {
        val exceptions = ArrayList<Throwable>()
        for ((_, validator) in chain) {
            val result = runCatching { validator.validate(token, scopes) }
            if (result.isSuccess) {
                return result.getOrThrow()
            } else {

            }
        }

        if (log.isDebugEnabled) {
            log.debug("Unable to decode token $token")
            log.debug("Stack traces follows:")
            exceptions.forEach { log.debug(it.stackTraceToString()) }
        }

        throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
