package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.UCloudApiOwnedBy
import kotlinx.serialization.Serializable

@Serializable
data class NormalizedToolDescription(
    val info: NameAndVersion,
    @Deprecated("Use image instead")
    val container: String? = null,
    val defaultNumberOfNodes: Int,
    val defaultTimeAllocation: SimpleDuration,
    val requiredModules: List<String>,
    val authors: List<String>,
    val title: String,
    val description: String,
    val backend: ToolBackend,
    val license: String,
    val image: String? = null,
    val supportedProviders: List<String>? = null,
) {
    override fun toString(): String {
        return "NormalizedToolDescription(info=$info, container='$container')"
    }
}

@Serializable
data class ToolReference(
    override val name: String,
    override val version: String,
    val tool: Tool? = null,
) : WithNameAndVersion

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
data class Tool(
    val owner: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: NormalizedToolDescription
)
