package dk.sdu.cloud.accounting.api.providers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import dk.sdu.cloud.provider.api.Resources
import kotlinx.serialization.Serializable

interface ProductSupport {
    val product: ProductReference
}

@Serializable
@UCloudApiOwnedBy(Resources::class)
data class ResolvedSupport<P : Product, Support : ProductSupport>(
    val product: P,
    val support: Support
)