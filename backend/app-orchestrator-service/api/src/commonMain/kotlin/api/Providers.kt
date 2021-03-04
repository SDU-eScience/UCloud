package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.provider.api.ManifestFeatureSupport
import kotlinx.serialization.Serializable

@Deprecated("Replaced with artifacts from provider-service")
@Serializable
data class ComputeProvider(
    val id: String,
    val domain: String,
    val https: Boolean,
    val port: Int? = null,
)

typealias ProviderManifest = dk.sdu.cloud.provider.api.ProviderManifest
typealias ManifestFeatureSupport = ManifestFeatureSupport

@Deprecated("Replaced with artifacts from provider-service")
@Serializable
data class ComputeProviderManifest(
    val metadata: ComputeProvider,
    val manifest: ProviderManifest,
)
