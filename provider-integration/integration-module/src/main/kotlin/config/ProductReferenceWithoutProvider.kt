package dk.sdu.cloud.config

import kotlinx.serialization.Serializable

@Serializable
data class ProductReferenceWithoutProvider(
    val id: String,
    val category: String,
) {
    override fun toString(): String {
        return "$id / $category"
    }
}
