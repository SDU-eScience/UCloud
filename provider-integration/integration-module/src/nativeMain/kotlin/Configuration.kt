package dk.sdu.cloud

import kotlinx.serialization.*

@Serializable
data class ProductReferenceWithoutProvider(
    val id: String,
    val category: String,
) {
    override fun toString(): String {
        return "$id / $category"
    }
}

sealed class ConfigurationException(message: String) : RuntimeException(message) {
    class IsBeingInstalled() : ConfigurationException("UCloud/IM is currently being installed")

    class BadConfiguration(message: String) : ConfigurationException(message)
}

