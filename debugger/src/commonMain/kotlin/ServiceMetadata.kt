package dk.sdu.cloud.debug

import kotlinx.serialization.Serializable

@Serializable
data class ServiceMetadata(
    val path: String,
    var id: Int = 0,
)
