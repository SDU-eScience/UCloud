package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.Actor
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.Provider
import dk.sdu.cloud.provider.api.ProviderSpecification
import dk.sdu.cloud.provider.api.Providers
import dk.sdu.cloud.provider.api.ProvidersRetrieveSpecificationRequest
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.SimpleCache
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

interface ProviderComms {
    val client: AuthenticatedClient
    val wsClient: AuthenticatedClient
    val provider: ProviderSpecification
}

data class SimpleProviderCommunication(
    override val client: AuthenticatedClient,
    override val wsClient: AuthenticatedClient,
    override val provider: ProviderSpecification
) : ProviderComms

class Providers<Communication : ProviderComms>(
    private val serviceClient: AuthenticatedClient,
    private val communicationFactory: (comms: ProviderComms) -> Communication
) {
    private val rpcClient = serviceClient.withoutAuthentication()

    val placeholderCommunication = runBlocking {
        val spec = ProviderSpecification(Provider.UCLOUD_CORE_PROVIDER, "192.0.2.100", false, 80)
        val client = AuthenticatedClient(rpcClient.client, OutgoingHttpCall) {}
        val wsClient = AuthenticatedClient(rpcClient.client, OutgoingWSCall) {}
        val simpleComms = SimpleProviderCommunication(client, wsClient, spec)
        communicationFactory(simpleComms)
    }

    private val communicationCache = SimpleCache<String, Communication>(
        maxAge = 60_000 * 15,
        lookup = { provider ->
            val auth = RefreshingJWTAuthenticator(
                rpcClient.client,
                JwtRefresher.ProviderOrchestrator(serviceClient, provider)
            )

            val providerSpec = Providers.retrieveSpecification.call(
                ProvidersRetrieveSpecificationRequest(provider),
                serviceClient
            ).orThrow()

            val hostInfo = HostInfo(providerSpec.domain, if (providerSpec.https) "https" else "http", providerSpec.port)
            val httpClient = auth.authenticateClient(OutgoingHttpCall).withFixedHost(hostInfo)
            val wsClient = auth.authenticateClient(OutgoingWSCall).withFixedHost(hostInfo)

            val simpleComms = SimpleProviderCommunication(httpClient, wsClient, providerSpec)

            println("DEBUG DEBUG DEBUG $provider => $hostInfo DEBUG DEBUG DEBUG")

            communicationFactory(simpleComms)
        }
    )

    /**
     * Prepares communication with a given provider represented by an actor
     * @throws RPCException (Internal Server Error) in case of unknown providers
     */
    suspend fun prepareCommunication(actor: Actor): Communication {
        return prepareCommunication(actor.safeUsername().removePrefix(PROVIDER_USERNAME_PREFIX))
    }

    /**
     * Prepares communication with a given provider
     * @throws RPCException (Internal Server Error) in case of unknown providers
     */
    suspend fun prepareCommunication(provider: String): Communication {
        if (provider == "") throw IllegalArgumentException()
        return communicationCache.get(provider)
            ?: throw RPCException("Unknown provider: $provider", HttpStatusCode.InternalServerError)
    }

    /**
     * Verifies that a given actor can act on the behalf of the provider
     */
    suspend fun verifyProvider(provider: String, principal: Actor) {
        if (provider != principal.safeUsername().removePrefix(PROVIDER_USERNAME_PREFIX)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }
    }

    companion object {
        const val PROVIDER_USERNAME_PREFIX = "#P_"

    }
}
