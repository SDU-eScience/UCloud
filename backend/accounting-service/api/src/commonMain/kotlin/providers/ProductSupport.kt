package dk.sdu.cloud.accounting.api.providers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference

interface ProductSupport {
    val product: ProductReference
}

data class ResolvedSupport<P : Product, Support : ProductSupport>(
    val product: P,
    val support: Support
)
