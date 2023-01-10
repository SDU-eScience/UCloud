package dk.sdu.cloud.integration.utils

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.integration.serviceClient
import dk.sdu.cloud.provider.api.ProviderSpecification
import dk.sdu.cloud.provider.api.Providers

const val OTHER_PROVIDER = "NewProvider"
const val UCLOUD_PROVIDER = "ucloud"

val sampleIngress = Product.Ingress(
    "u1-ingress",
    1,
    ProductCategoryId("ingress", UCLOUD_PROVIDER),
    description = "product description"
)

val sampleCompute = Product.Compute(
    "u1-standard-1",
    100_000,
    ProductCategoryId("standard", UCLOUD_PROVIDER),
    cpu = 1,
    memoryInGigs = 1,
    description = "product description"
)

val sampleCompute2 = sampleCompute.copy(
    name = "u1-standard-2",
    cpu = 2,
    memoryInGigs = 2
)

val sampleCompute3 = sampleCompute.copy(
    name = "u1-standard-3",
    cpu = 3,
    memoryInGigs = 3
)

val sampleCompute4 = sampleCompute.copy(
    name = "u1-standard-4",
    cpu = 4,
    memoryInGigs = 4
)

val sampleCompute5 = sampleCompute.copy(
    name = "u1-standard-5",
    cpu = 5,
    memoryInGigs = 5
)

val sampleComputeOtherProvider = sampleCompute.copy(
    category = ProductCategoryId("standard", OTHER_PROVIDER)
)

val sampleStorageDifferential = Product.Storage(
    "u1-cephfs-quota",
    1L,
    ProductCategoryId("cephfs-quota", UCLOUD_PROVIDER),
    chargeType = ChargeType.DIFFERENTIAL_QUOTA,
    description = "product description",
    unitOfPrice = ProductPriceUnit.PER_UNIT
)

val sampleStorage = Product.Storage(
    "u1-cephfs",
    100_000,
    ProductCategoryId("cephfs", UCLOUD_PROVIDER),
    description = "product description"
)

val sampleStorage2 = sampleStorage.copy(
    name = "u2-cephfs"
)

val sampleStorageOtherProvider = sampleStorage.copy(
    category = ProductCategoryId("cephfs", OTHER_PROVIDER)
)

val sampleNetworkIp = Product.NetworkIP(
    "u1-public-ip",
    1,
    ProductCategoryId("public-ip", UCLOUD_PROVIDER),
    description = "product description"
)

val sampleProducts = listOf(sampleCompute, sampleStorage, sampleIngress, sampleNetworkIp, sampleStorageDifferential,
    sampleCompute2, sampleCompute3, sampleCompute4, sampleCompute5)
val sampleProductsOtherProvider = listOf(sampleComputeOtherProvider, sampleStorageOtherProvider)

suspend fun createProvider(providerName: String = UCLOUD_PROVIDER) {
    Providers.create.call(
        bulkRequestOf(
            ProviderSpecification(
                providerName,
                "localhost",
                https = false,
                port = 8080
            )
        ),
        adminClient
    ).orThrow()
}

/**
 * Creates a sample catalog of products
 */
suspend fun createSampleProducts() {
    try {
        createProvider()
    } catch (ex: RPCException) {
        if (ex.httpStatusCode == HttpStatusCode.Conflict) {
            println("Provider already exists")
        }
        else {
            throw ex
        }
    }
    for (product in sampleProducts) {
        Products.create.call(
            BulkRequest(listOf(product)),
            serviceClient
        ).orThrow()
    }
}
