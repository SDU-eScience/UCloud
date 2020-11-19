package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.calls.CallDescriptionContainer

data class ComputeProvider(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int? = null,
)

data class ProviderManifest(
    val features: ManifestFeatureSupport = ManifestFeatureSupport(),
)

data class ManifestFeatureSupport(
    val compute: Compute = Compute(),
) {
    data class Compute(
        val docker: Docker = Docker(),
        val virtualMachine: VirtualMachine = VirtualMachine(),
    ) {
        data class Docker(
            var enabled: Boolean = false,
            var web: Boolean = false,
            var vnc: Boolean = false,
            var batch: Boolean = false,
            var logs: Boolean = false,
            var terminal: Boolean = false,
            var peers: Boolean = false,
        )

        data class VirtualMachine(
            var enabled: Boolean = false,
            var logs: Boolean = false,
            var vnc: Boolean = false,
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

