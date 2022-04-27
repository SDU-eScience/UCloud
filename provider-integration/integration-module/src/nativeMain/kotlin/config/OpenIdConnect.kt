package dk.sdu.cloud.config

import kotlinx.serialization.*

@Serializable
@SerialName("OpenIdConnect")
data class OpenIdConnectConfiguration(
    val certificate: String,
    val mappingTimeToLive: Ttl,
    val endpoints: Endpoints,
    val client: Client,
    val extensions: Extensions,
    val redirectUrl: String? = null,
) : ConfigSchema.Plugins.Connection() {
    @Serializable
    data class Ttl(
        val days: Int = 0,
        val hours: Int = 0,
        val minutes: Int = 0,
        val seconds: Int = 0,
    )

    @Serializable
    data class Endpoints(
        val auth: String,
        val token: String,
    )

    @Serializable
    data class Client(
        val id: String,
        val secret: String
    )

    @Serializable
    data class Extensions(
        val onConnectionComplete: String,
    )
}

