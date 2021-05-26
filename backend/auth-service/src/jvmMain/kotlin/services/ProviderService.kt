package dk.sdu.cloud.auth.services

import com.auth0.jwt.algorithms.Algorithm
import com.onelogin.saml2.util.Util
import dk.sdu.cloud.*
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateKey
import java.util.*

class ProviderService(
    private val devMode: Boolean,
    private val db: AsyncDBSessionFactory,
    private val dao: ProviderDao,
) {
    suspend fun renewToken(
        request: BulkRequest<AuthProvidersRenewRequestItem>,
    ): BulkResponse<PublicKeyAndRefreshToken> {
        return BulkResponse(db.withSession { session ->
            request.items.map { req ->
                val newKeys = generateKeys()
                dao.renew(session, ActorAndProject(Actor.System, null), req.id, newKeys)

                PublicKeyAndRefreshToken(req.id, newKeys.publicKey, newKeys.refreshToken)
            }
        })
    }

    suspend fun registerProvider(
        request: BulkRequest<AuthProvidersRegisterRequestItem>,
    ): BulkResponse<AuthProvidersRegisterResponseItem> {
        return BulkResponse(db.withSession { session ->
            request.items.map { req ->
                val keys = generateKeys()
                dao.register(session, ActorAndProject(Actor.System, null), req.id, keys)
                AuthProvidersRegisterResponseItem(keys.claimToken)
            }
        })
    }

    suspend fun claimRegistration(
        request: BulkRequest<AuthProvidersRegisterResponseItem>,
    ): BulkResponse<PublicKeyAndRefreshToken> {
        return BulkResponse(db.withSession { session ->
            request.items.map { req ->
                val claimed = dao.claim(session, ActorAndProject(Actor.System, null), req.claimToken)
                PublicKeyAndRefreshToken(claimed.id, claimed.publicKey, claimed.refreshToken)
            }
        })
    }

    /*
    1) provider -> auth: register
    2) provider -> provider: write all info
    3) provider -> provider: commit
    4) provider -> auth: claim
    5) provider -> provider: commit claim

    before 1: Not an issue
    after 1: Not an issue, nothing has been committed
    after 2: Not an issue, nothing has been committed
    after 3: Kind of an issue, auth doesn't know that this has been completed
    after 4(provider): Not an issue, everything is OK
    after 4(auth): Not an issue, provider will retry later

    provider will notice that it did not claim and retry. If it is a retry then auth will reply OK and allow provider
    to continue. If the claim is a duplicate then data must be rolled back by a sysadmin since state is obviously not
    working as it should.
     */

    suspend fun refreshToken(request: AuthProvidersRefreshRequest): BulkResponse<Pair<String, AccessToken>> {
        return BulkResponse(db.withSession { session ->
            request.items.map { req ->
                val provider = dao.retrieveByRefreshToken(session, req.refreshToken)
                provider.id to signToken(
                    provider,
                    AccessTokenContents(
                        ProviderPrincipal(AuthProviders.PROVIDER_PREFIX + provider.id),
                        listOf(SecurityScope.ALL_WRITE),
                        Time.now(),
                        Time.now() + (1000L * 60 * 15)
                    )
                )
            }
        })
    }

    private fun signToken(provider: InternalProvider, contents: AccessTokenContents): AccessToken {
        val privateKey = Util.loadPrivateKey(provider.privateKey) as RSAPrivateKey
        val jwtAlg = Algorithm.RSA256(null, privateKey)
        val factory = JWTFactory(jwtAlg, disable2faCheck = true)
        return AccessToken(factory.generate(contents))
    }

    suspend fun refreshTokenAsOrchestrator(
        actorAndProject: ActorAndProject,
        request: AuthProvidersRefreshAsProviderRequest,
    ): AuthProvidersRefreshAsProviderResponse {
        val (actor) = actorAndProject
        if (!devMode && (actor != Actor.System && actor.safeUsername() !in whitelistedProviders)) {
            throw RPCException("You have not been pre-authorized to perform this call", HttpStatusCode.Forbidden)
        }

        return BulkResponse(db.withSession { session ->
            request.items.map { req ->
                // NOTE: We change the actor on purpose here
                val provider = dao.retrieve(session, ActorAndProject(Actor.System, null), req.providerId)

                signToken(
                    provider,
                    AccessTokenContents(
                        ServicePrincipal("_UCloud", Role.SERVICE),
                        listOf(SecurityScope.ALL_WRITE),
                        Time.now(),
                        Time.now() + (1000L * 60 * 15)
                    )
                )
            }
        })
    }

    suspend fun retrievePublicKey(providerId: String): String {
        return dao.retrieve(db, ActorAndProject(Actor.System, null), providerId).publicKey
    }

    fun generateKeys(): ProviderKeys {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val keyPair = keyGen.genKeyPair()
        val public = keyPair.public.encoded.let { Base64.getEncoder().encodeToString(it) }
        val private = keyPair.private.encoded.let { Base64.getEncoder().encodeToString(it) }

        val claimToken = ByteArray(64).also { random.nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }
        val refreshToken = ByteArray(64).also { random.nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }

        return ProviderKeys(public, private, refreshToken, claimToken)
    }

    companion object {
        private val random = SecureRandom()

        val whitelistedProviders = listOf(
            "_app-orchestrator"
        )
    }
}
