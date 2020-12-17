package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.app.orchestrator.api.Compute
import dk.sdu.cloud.app.orchestrator.api.ComputeProviderManifest
import dk.sdu.cloud.app.orchestrator.api.IngressProvider
import dk.sdu.cloud.app.orchestrator.api.LicenseProvider
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.safeUsername
import io.ktor.http.*

data class ProviderCommunication(
    val api: Compute,
    val client: AuthenticatedClient,
    val wsClient: AuthenticatedClient,
    val provider: ComputeProviderManifest,
    val ingressApi: IngressProvider?,
    val licenseApi: LicenseProvider?,
)

class Providers(
    private val developmentModeEnabled: Boolean,
    private val serviceClient: AuthenticatedClient,
    private val wsServiceClient: AuthenticatedClient,
    private val hardcodedProvider: ComputeProviderManifest,
) {
    private val ucloudCompute = Compute(UCLOUD_PROVIDER)

    suspend fun prepareCommunication(actor: Actor): ProviderCommunication {
        return prepareCommunication(actor.safeUsername().removePrefix(PROVIDER_USERNAME_PREFIX))
    }

    suspend fun prepareCommunication(provider: String): ProviderCommunication {
        if (developmentModeEnabled) {
            return ProviderCommunication(
                ucloudCompute,
                serviceClient,
                wsServiceClient,
                hardcodedProvider,
                IngressProvider(UCLOUD_PROVIDER),
                LicenseProvider(UCLOUD_PROVIDER),
            )
        }
        if (provider != UCLOUD_PROVIDER) {
            throw RPCException("Unknown provider: $provider", HttpStatusCode.InternalServerError)
        }

        return ProviderCommunication(
            ucloudCompute,
            serviceClient,
            wsServiceClient,
            hardcodedProvider,
            IngressProvider(UCLOUD_PROVIDER),
            LicenseProvider(UCLOUD_PROVIDER),
        )
    }

    suspend fun verifyProvider(provider: String, principal: Actor) {
        if (developmentModeEnabled) return
        if (provider != UCLOUD_PROVIDER) {
            throw RPCException("Unknown provider: $provider", HttpStatusCode.InternalServerError)
        }

        if (principal.safeUsername() != "_app-kubernetes") {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }
    }

    suspend fun fetchManifest(provider: String): ComputeProviderManifest {
        if (provider != UCLOUD_PROVIDER) {
            throw RPCException("Unknown provider: $provider", HttpStatusCode.InternalServerError)
        }

        return hardcodedProvider
    }

    companion object {
        const val PROVIDER_USERNAME_PREFIX = "#P"
    }
}
