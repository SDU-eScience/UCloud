package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.provider.api.ManifestFeatureSupport

@Deprecated("Replaced with artifacts from provider-service")
data class ComputeProvider(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int? = null,
)

typealias ProviderManifest = dk.sdu.cloud.provider.api.ProviderManifest
typealias ManifestFeatureSupport = ManifestFeatureSupport

@Deprecated("Replaced with artifacts from provider-service")
data class ComputeProviderManifest(
    val metadata: ComputeProvider,
    val manifest: ProviderManifest,
)
