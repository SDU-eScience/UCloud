package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.calls.CallDescriptionContainer

data class ComputeProvider(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int? = null,
)

data class ComputeProviderManifestBody(
    val features: ManifestFeatureSupport,
)

data class ManifestFeatureSupport(
    val web: Boolean = false,
    val vnc: Boolean = false,
    val batch: Boolean = false,
    val docker: Boolean = false,
    val virtualMachine: Boolean = false,
    val logs: Boolean = false,
)

data class ComputeProviderManifest(
    val metadata: ComputeProvider,
    val manifest: ComputeProviderManifestBody
)

object Providers : CallDescriptionContainer("jobs.providers") {

}

