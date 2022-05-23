package dk.sdu.cloud.debug

import kotlinx.serialization.Serializable

@Serializable
data class ServiceMetadata(
    val id: String,
    val path: String,
)
