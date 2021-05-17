package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.provider.api.Providers as ProvidersApi
import io.ktor.http.*
import kotlinx.coroutines.delay

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
        maxAge = 60_000 * 15L,
        lookup = { provider ->
            val auth = RefreshingJWTAuthenticator(
                rpcClient.client,
                JwtRefresher.ProviderOrchestrator(serviceClient, provider)
            )

            val providerSpec = ProvidersApi.retrieveSpecification.call(
                ProvidersRetrieveSpecificationRequest(provider),
                serviceClient
            ).orThrow()

            val hostInfo = HostInfo(providerSpec.domain, if (providerSpec.https) "https" else "http", providerSpec.port)
            val httpClient = auth.authenticateClient(OutgoingHttpCall).withFixedHost(hostInfo)
            val wsClient = auth.authenticateClient(OutgoingWSCall).withFixedHost(hostInfo)

            // TODO We don't know which are actually valid
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

    suspend fun retrieveProviderSpecification(provider: String): ProviderSpecification {
        return prepareCommunication(provider).provider
    }

    /**
     * Verifies that a given actor is a valid provider and returns the provider's id
     */
    suspend fun verifyProviderIsValid(actor: Actor): String {
        prepareCommunication(actor)
        return actor.safeUsername().removePrefix(PROVIDER_USERNAME_PREFIX)
    }

    suspend fun <R : Any, S : Any, E : Any> proxyCall(
        provider: String,
        endUser: Actor?,
        call: (ProviderCommunication) -> CallDescription<R, S, E>,
        request: R,
        useWebsockets: Boolean = false,
    ): IngoingCallResponse<S, E> {
        val comms = prepareCommunication(provider)
        val rpc = call(comms)
        val authenticatedClient = if (useWebsockets) comms.wsClient else comms.client
        val username = when (endUser) {
            Actor.System -> null
            is Actor.SystemOnBehalfOfUser -> endUser.username
            is Actor.User -> endUser.username
            null -> null
        }

        for (attempt in 0 until 5) {
            val im = IntegrationProvider(provider)
            val response = rpc.call(request, authenticatedClient.withProxyInfo(username))
            if (username != null && response.statusCode == HttpStatusCode.RetryWith) {
                im.init.call(IntegrationProviderInitRequest(username), comms.client).orThrow()
                delay(50)
                continue
            }

            if (username != null && response.statusCode == HttpStatusCode.ServiceUnavailable) {
                log.debug("Waiting for server to start...")
                delay(250) // Server might still be starting
                continue
            }

            return response
        }

        throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
    }

    suspend fun <R : Any, S : Any, E : Any> proxySubscription(
        provider: String,
        endUser: Actor?,
        call: (ProviderCommunication) -> CallDescription<R, S, E>,
        request: R,
        handler: suspend (S) -> Unit,
    ): IngoingCallResponse<S, E> {
        val comms = prepareCommunication(provider)
        val rpc = call(comms)
        val authenticatedClient = comms.wsClient
        val username = when (endUser) {
            Actor.System -> null
            is Actor.SystemOnBehalfOfUser -> endUser.username
            is Actor.User -> endUser.username
            null -> null
        }

        for (attempt in 0 until 5) {
            val im = IntegrationProvider(provider)
            val response = rpc.subscribe(request, authenticatedClient.withProxyInfo(username), handler)
            if (username != null && response.statusCode == HttpStatusCode.RetryWith) {
                im.init.call(IntegrationProviderInitRequest(username), comms.client).orThrow()
                delay(100)
                continue
            }
            if (username != null && response.statusCode == HttpStatusCode.ServiceUnavailable) {
                log.debug("Waiting for server to start...")
                delay(250) // Server might still be starting
                continue
            }

            return response
        }

        throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
    }

    companion object : Loggable {
        const val PROVIDER_USERNAME_PREFIX = "#P_"
        override val log = logger()
    }
}

val HttpStatusCode.Companion.RetryWith get() = HttpStatusCode(449, "Retry With")
