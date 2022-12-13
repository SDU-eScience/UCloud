package dk.sdu.cloud.config

import dk.sdu.cloud.accounting.api.ProductReference
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

fun ProductReference.removeProvider(): ProductReferenceWithoutProvider {
    return ProductReferenceWithoutProvider(id, category)
}
