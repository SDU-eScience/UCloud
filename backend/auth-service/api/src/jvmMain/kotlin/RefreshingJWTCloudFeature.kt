package dk.sdu.cloud.auth.api

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTDecodeException
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.security.interfaces.RSAPublicKey

@Deprecated("Replace with AuthenticatorFeature", ReplaceWith("AuthenticatorFeature"), DeprecationLevel.WARNING)
typealias RefreshingJWTCloudFeature = AuthenticatorFeature

class AuthenticatorFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val clientContext = ctx.client
        val refreshToken = ctx.configuration.requestChunkAt<String>("refreshToken")

        val tokenValidation = ctx.tokenValidation as? InternalTokenValidationJWT
            ?: throw IllegalStateException("Token validation needs to use JWTs!")

        val authenticator = RefreshingJWTAuthenticator(clientContext, JwtRefresher.Normal(refreshToken))
        ctx.authenticator = authenticator

        @Suppress("UNCHECKED_CAST")
        ctx.tokenValidation = TokenValidationWithProviderSupport(
            tokenValidation,
            authenticator.authenticateClient(OutgoingHttpCall)
        ) as TokenValidation<Any>
    }

    companion object Feature : MicroFeatureFactory<AuthenticatorFeature, Unit> {
        override val key = MicroAttributeKey<AuthenticatorFeature>("refreshing-jwt-cloud-feature")
        override fun create(config: Unit): AuthenticatorFeature = AuthenticatorFeature()

        internal val REFRESHING_CLOUD_KEY = MicroAttributeKey<RefreshingJWTAuthenticator>("refreshing-cloud")
    }
}

class TokenValidationWithProviderSupport(
    val delegate: InternalTokenValidationJWT,
    private val serviceClient: AuthenticatedClient,
) : TokenValidation<DecodedJWT> {
    private val jwtDecoderWhichDoesNotVerifyThinkBeforeYouType = JWT()
    private val providerVerifier = SimpleCache<String, JWTVerifier>(maxAge = 60_000 * 15) { provider ->
        val publicKeyAsString = AuthProviders.retrievePublicKey.call(
            FindByStringId(provider),
            serviceClient
        ).orThrow().publicKey
        val publicKey = loadCert(publicKeyAsString)?.publicKey
            ?: throw RPCException("Could not read public key", HttpStatusCode.InternalServerError)

        JWT.require(Algorithm.RSA256(publicKey as RSAPublicKey, null)).run {
            withIssuer("cloud.sdu.dk")
            build()
        }
    }

    override fun decodeToken(token: DecodedJWT): SecurityPrincipalToken {
        return delegate.decodeToken(token)
    }

    override fun validate(token: String, scopes: List<SecurityScope>?): DecodedJWT {
        val decodedJwt = jwtDecoderWhichDoesNotVerifyThinkBeforeYouType.decodeJwt(token).toSecurityToken()
        return if (decodedJwt.principal.role == Role.PROVIDER) {
            val verifier = runBlocking {
                providerVerifier.get(decodedJwt.principal.username.removePrefix(AuthProviders.PROVIDER_PREFIX))
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Unauthorized)
            }

            try {
                verifier.verify(token)
            } catch (ex: JWTVerificationException) {
                throw TokenValidationException.Invalid()
            } catch (ex: JWTDecodeException) {
                throw TokenValidationException.Invalid()
            }
        } else {
            delegate.validate(token, scopes)
        }
    }
}

var Micro.authenticator: RefreshingJWTAuthenticator
    get() {
        requireFeature(AuthenticatorFeature)
        return attributes[AuthenticatorFeature.REFRESHING_CLOUD_KEY]
    }
    set(value) {
        attributes[AuthenticatorFeature.REFRESHING_CLOUD_KEY] = value
    }
