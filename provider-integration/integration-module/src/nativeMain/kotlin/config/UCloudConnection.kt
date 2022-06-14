package dk.sdu.cloud.config

import kotlinx.serialization.*

@Serializable
@SerialName("UCloud")
data class UCloudConnectionConfiguration(
    val redirectTo: String,
    val extensions: Extensions,

    // NOTE(Dan): The message signing protocol directly requires safe authentication between end-user and provider
    // directly. The UCloud connection plugin provides no such thing and implicitly trusts UCloud. This trust makes
    // message signing completely useless. As a result, message signing should only be turned on for the purposes of
    // testing the implementation in development.
    val insecureMessageSigningForDevelopmentPurposesOnly: Boolean = false,
): ConfigSchema.Plugins.Connection() {
    @Serializable
    data class Extensions(
        val onConnectionComplete: String
    )
}
