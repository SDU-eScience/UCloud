package dk.sdu.cloud.config

import dk.sdu.cloud.utils.YamlString
import kotlinx.serialization.*

@Serializable
@SerialName("Extension")
data class ExtensionAllocationConfig(
    val script: YamlString,
) : ConfigSchema.Plugins.Allocations()

