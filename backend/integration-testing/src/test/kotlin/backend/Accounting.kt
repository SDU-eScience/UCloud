package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient

val sampleCompute = Product.Compute(
    "u1-standard-1",
    100_000,
    ProductCategoryId("standard", UCLOUD_PROVIDER),
    cpu = 1,
    memoryInGigs = 4
)

val sampleStorage = Product.Storage(
    "u1-cephfs",
    100_000,
    ProductCategoryId("cephfs", UCLOUD_PROVIDER)
)

val sampleProducts = listOf(sampleCompute, sampleStorage)

/**
 * Creates a sample catalog of products
 */
suspend fun createSampleProducts() {
    sampleProducts.forEach { product ->
        Products.createProduct.call(
            product,
            serviceClient
        ).orThrow()
    }
}
