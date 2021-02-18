package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.provider.api.ManifestFeatureSupport
import dk.sdu.cloud.app.orchestrator.api.*
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
    val networkApi: NetworkIPProvider?,
)

class Providers(
    private val developmentModeEnabled: Boolean,
    private val serviceClient: AuthenticatedClient,
    private val wsServiceClient: AuthenticatedClient,
    private val hardcodedProvider: ComputeProviderManifest,
) {
    private val ucloudCompute = Compute(UCLOUD_PROVIDER)

    private val aauManifest = hardcodedProvider.copy(
        metadata = hardcodedProvider.metadata.copy(id = "aau"),
        manifest = ProviderManifest(
            ManifestFeatureSupport(
                compute = ManifestFeatureSupport.Compute(
                    docker = ManifestFeatureSupport.Compute.Docker(enabled = false),
                    virtualMachine = ManifestFeatureSupport.Compute.VirtualMachine(enabled = true)
                )
            )
        ),
    )

    suspend fun prepareCommunication(actor: Actor): ProviderCommunication {
        return prepareCommunication(actor.safeUsername().removePrefix(PROVIDER_USERNAME_PREFIX))
    }

    suspend fun prepareCommunication(provider: String): ProviderCommunication {
        if (provider == "aau" || provider == "_app-aau") {
            // Hackity, hack, hack. TODO Remove this later, please.
            return ProviderCommunication(
                Compute("aau"),
                serviceClient,
                wsServiceClient,
                aauManifest,
                null,
                null,
                null,
            )
        }

        if (provider == UCLOUD_PROVIDER || provider == "_app-kubernetes") {
            return ProviderCommunication(
                ucloudCompute,
                serviceClient,
                wsServiceClient,
                hardcodedProvider,
                IngressProvider(UCLOUD_PROVIDER),
                LicenseProvider(UCLOUD_PROVIDER),
                NetworkIPProvider(UCLOUD_PROVIDER),
            )
        }

        if (developmentModeEnabled) {
            return ProviderCommunication(
                ucloudCompute,
                serviceClient,
                wsServiceClient,
                hardcodedProvider,
                IngressProvider(UCLOUD_PROVIDER),
                LicenseProvider(UCLOUD_PROVIDER),
                NetworkIPProvider(UCLOUD_PROVIDER),
            )
        }

        throw RPCException("Unknown provider: $provider", HttpStatusCode.InternalServerError)
    }

    suspend fun verifyProvider(provider: String, principal: Actor) {
        if (developmentModeEnabled) return
        if (provider == "aau" && principal.safeUsername() == "_app-aau") return
        if (provider == UCLOUD_PROVIDER && principal.safeUsername() == "_app-kubernetes") return

        throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
    }

    suspend fun fetchManifest(provider: String): ComputeProviderManifest {
        if (provider == UCLOUD_PROVIDER) return hardcodedProvider
        if (provider == "aau") return aauManifest

        throw RPCException("Unknown provider: $provider", HttpStatusCode.InternalServerError)
    }

    companion object {
        const val PROVIDER_USERNAME_PREFIX = "#P"
    }
}
