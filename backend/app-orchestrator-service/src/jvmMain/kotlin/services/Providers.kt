package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.ProviderSpecification
import dk.sdu.cloud.provider.api.ProvidersRetrieveSpecificationRequest
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.provider.api.Providers as ProvidersApi
import io.ktor.http.*

data class ProviderCommunication(
    val api: JobsProvider,
    val client: AuthenticatedClient,
    val wsClient: AuthenticatedClient,
    val ingressApi: IngressProvider?,
    val licenseApi: LicenseProvider?,
    val networkApi: NetworkIPProvider?,
    val provider: ProviderSpecification,
)

class Providers(
    private val serviceClient: AuthenticatedClient,
) {
    private val rpcClient = serviceClient.withoutAuthentication()

    private val communicationCache = SimpleCache<String, ProviderCommunication>(
        maxAge = 60_000 * 15,
        lookup = { provider ->
            val auth = RefreshingJWTAuthenticator(
                rpcClient.client,
                JwtRefresher.ProviderOrchestrator(serviceClient, provider)
            )

            val providerSpec = ProvidersApi.retrieveSpecification.call(
                ProvidersRetrieveSpecificationRequest(provider),
                serviceClient
            ).orRethrowAs {
                throw RPCException("Unknown service provider: $provider", HttpStatusCode.InternalServerError)
            }

            val hostInfo = HostInfo(providerSpec.domain, if (providerSpec.https) "https" else "http", providerSpec.port)
            val httpClient = auth.authenticateClient(OutgoingHttpCall).withFixedHost(hostInfo)
            val wsClient = auth.authenticateClient(OutgoingWSCall).withFixedHost(hostInfo)

            val ingressApi = IngressProvider(provider)
            val licenseApi = LicenseProvider(provider)
            val networkApi = NetworkIPProvider(provider)
            val computeApi = JobsProvider(provider)

            ProviderCommunication(computeApi, httpClient, wsClient, ingressApi, licenseApi, networkApi, providerSpec)
        }
    )

    /**
     * Prepares communication with a given provider represented by an actor
     * @throws RPCException (Internal Server Error) in case of unknown providers
     */
    suspend fun prepareCommunication(actor: Actor): ProviderCommunication {
        return prepareCommunication(actor.safeUsername().removePrefix(PROVIDER_USERNAME_PREFIX))
    }

    /**
     * Prepares communication with a given provider
     * @throws RPCException (Internal Server Error) in case of unknown providers
     */
    suspend fun prepareCommunication(provider: String): ProviderCommunication {
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

    companion object : Loggable {
        override val log = logger()
        const val PROVIDER_USERNAME_PREFIX = "#P_"
    }
}
