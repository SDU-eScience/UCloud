package dk.sdu.cloud.config

import kotlinx.serialization.*

@Serializable
@SerialName("UCloud")
data class UCloudConnectionConfiguration(
    val redirectTo: String,
    val extensions: Extensions,
): ConfigSchema.Plugins.Connection() {
    @Serializable
    data class Extensions(
        val onConnectionComplete: String
    )
}
