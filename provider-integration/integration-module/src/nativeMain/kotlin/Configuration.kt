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
