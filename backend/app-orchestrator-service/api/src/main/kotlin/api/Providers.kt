package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.UCloudApiDoc

data class ComputeProvider(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int? = null,
)

@UCloudApiDoc("""The `ProviderManifest` contains general metadata about the provider.

The manifest, for example, includes information about which `features` are supported by a provider. """)
data class ProviderManifest(
    @UCloudApiDoc("Contains information about the features supported by this provider")
    val features: ManifestFeatureSupport = ManifestFeatureSupport(),
)

@UCloudApiDoc("""Contains information about the features supported by this provider
    
Features are by-default always disabled. There is _no_ minimum set of features a provider needs to support.""")
data class ManifestFeatureSupport(
    @UCloudApiDoc("Determines which compute related features are supported by this provider")
    val compute: Compute = Compute(),
) {
    data class Compute(
        @UCloudApiDoc("Support for `Tool`s using the `DOCKER` backend")
        val docker: Docker = Docker(),

        @UCloudApiDoc("Support for `Tool`s using the `VIRTUAL_MACHINE` backend")
        val virtualMachine: VirtualMachine = VirtualMachine(),
    ) {
        data class Docker(
            @UCloudApiDoc("Flag to enable/disable this feature\n\nAll other flags are ignored if this is `false`.")
            var enabled: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the interactive interface of `WEB` `Application`s")
            var web: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the interactive interface of `VNC` `Application`s")
            var vnc: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable `BATCH` `Application`s")
            var batch: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the log API")
            var logs: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the interactive terminal API")
            var terminal: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable connection between peering `Job`s")
            var peers: Boolean = false,
        )

        data class VirtualMachine(
            @UCloudApiDoc("Flag to enable/disable this feature\n\nAll other flags are ignored if this is `false`.")
            var enabled: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the log API")
            var logs: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the VNC API")
            var vnc: Boolean = false,
            @UCloudApiDoc("Flag to enable/disable the interactive terminal API")
            var terminal: Boolean = false,
        )
    }
}

data class ComputeProviderManifest(
    val metadata: ComputeProvider,
    val manifest: ProviderManifest,
)

object Providers : CallDescriptionContainer("jobs.providers") {

}
