package dk.sdu.cloud.config

import dk.sdu.cloud.utils.YamlString
import kotlinx.serialization.*

@Serializable
@SerialName("Extension")
data class ExtensionAllocationConfig(
    val extensions: Extensions,
) : ConfigSchema.Plugins.Allocations() {
    @Serializable
    data class Extensions(
        val onAllocation: YamlString,
        val onSynchronization: YamlString,
    )
}
