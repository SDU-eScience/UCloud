package dk.sdu.cloud.accounting.api.providers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import kotlinx.serialization.Serializable

interface ProductSupport {
    val product: ProductReference
}

@Serializable
data class ResolvedSupport<P : Product, Support : ProductSupport>(
    val product: P,
    val support: Support
)