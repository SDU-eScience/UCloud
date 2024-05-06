package dk.sdu.cloud.integration.utils

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.integration.adminClient
import dk.sdu.cloud.integration.serviceClient
import dk.sdu.cloud.project.api.v2.Project
import dk.sdu.cloud.project.api.v2.Projects
import dk.sdu.cloud.provider.api.ProviderSpecification
import dk.sdu.cloud.provider.api.Providers
import dk.sdu.cloud.provider.api.basicTranslationToAccountingUnit
import dk.sdu.cloud.provider.api.translateToAccountingFrequency
import dk.sdu.cloud.singleIdOrNull

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

fun productV1toV2(product: Product): ProductV2 {
    val category = ProductCategory(
        product.category.name,
        product.category.provider,
        product.productType,
        basicTranslationToAccountingUnit(product.unitOfPrice, product.productType),
        translateToAccountingFrequency(product.unitOfPrice),
        product.freeToUse
    )

    return when (product) {
        is Product.Compute -> {
            ProductV2.Compute(
                name = product.name,
                price = product.pricePerUnit,
                category = category,
                description = product.description,
                cpu = product.cpu,
                memoryInGigs = product.memoryInGigs,
                gpu = product.gpu,
                cpuModel = product.cpuModel,
                memoryModel = product.memoryModel,
                gpuModel = product.gpuModel,
                hiddenInGrantApplications = product.hiddenInGrantApplications
            )
        }

        is Product.Storage -> {
            ProductV2.Storage(
                name = product.name,
                price = product.pricePerUnit,
                category = category,
                description = product.description,
                hiddenInGrantApplications = product.hiddenInGrantApplications
            )
        }

        is Product.License -> {
            ProductV2.License(
                name = product.name,
                price = product.pricePerUnit,
                category = category,
                description = product.description,
                hiddenInGrantApplications = product.hiddenInGrantApplications,
                tags = product.tags
            )
        }

        is Product.NetworkIP -> {
            ProductV2.NetworkIP(
                name = product.name,
                price = product.pricePerUnit,
                category = category,
                description = product.description,
                hiddenInGrantApplications = product.hiddenInGrantApplications
            )
        }

        is Product.Ingress -> {
            ProductV2.Ingress(
                name = product.name,
                price = product.pricePerUnit,
                category = category,
                description = product.description,
                hiddenInGrantApplications = product.hiddenInGrantApplications
            )
        }

        else -> {
            throw RPCException("Unknown Product Type", HttpStatusCode.InternalServerError)
        }
    }

}

suspend fun createProvider(providerName: String = UCLOUD_PROVIDER, providerProject: String) {
    Providers.create.call(
        bulkRequestOf(
            ProviderSpecification(
                providerName,
                "localhost",
                https = false,
                port = 8080
            )
        ),
        adminClient.withProject(providerProject)
    ).orThrow()
}

/**
 * Creates a sample catalog of products
 */
suspend fun createSampleProducts(providerProject: String) {
    try {
        createProvider(generateId("provider"), providerProject)
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
