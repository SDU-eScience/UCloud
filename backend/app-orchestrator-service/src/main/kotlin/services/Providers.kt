package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.app.orchestrator.api.Compute
import dk.sdu.cloud.app.orchestrator.api.ComputeProvider
import dk.sdu.cloud.app.orchestrator.api.ComputeProviderManifest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import io.ktor.http.*

data class ProviderCommunication(
    val api: Compute,
    val client: AuthenticatedClient,
)

class Providers(
    private val serviceClient: AuthenticatedClient,
    private val hardcodedProvider: ComputeProviderManifest,
) {
    private val ucloudCompute = Compute("kubernetes")

    suspend fun prepareCommunication(provider: String): ProviderCommunication {
        if (provider != UCLOUD_PROVIDER) {
            throw RPCException("Unknown provider: $provider", HttpStatusCode.InternalServerError)
        }

        return ProviderCommunication(ucloudCompute, serviceClient)
    }

    suspend fun verifyProvider(provider: String, principal: SecurityPrincipal) {
        if (provider != UCLOUD_PROVIDER) {
            throw RPCException("Unknown provider: $provider", HttpStatusCode.InternalServerError)
        }

        if (principal.username != "_app-kubernetes") {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }
    }

    suspend fun fetchManifest(provider: String): ComputeProviderManifest {
        if (provider != UCLOUD_PROVIDER) {
            throw RPCException("Unknown provider: $provider", HttpStatusCode.InternalServerError)
        }

        return hardcodedProvider
    }
}
