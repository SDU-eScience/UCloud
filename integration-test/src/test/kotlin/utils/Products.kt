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
import dk.sdu.cloud.integration.adminUsername
import dk.sdu.cloud.integration.serviceClient
import dk.sdu.cloud.project.api.v2.Project
import dk.sdu.cloud.project.api.v2.Projects
import dk.sdu.cloud.project.api.v2.ProjectsBrowseRequest
import dk.sdu.cloud.project.api.v2.ProjectsRetrieveRequest
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.singleIdOrNull

const val OTHER_PROVIDER = "NewProvider"
const val UCLOUD_PROVIDER = "ucloud"

val sampleIngress = ProductV2.Ingress(
    "u1-ingress",
    1,
    ProductCategory(
        name = "ingress",
        provider = UCLOUD_PROVIDER,
        accountingFrequency = AccountingFrequency.ONCE,
        accountingUnit = AccountingUnit(
            name = "this",
            namePlural = "those",
            floatingPoint = false,
            displayFrequencySuffix = false
        ),
        productType = ProductType.INGRESS
    ),
    description = "product description"
)

val sampleCompute = ProductV2.Compute(
    "u1-standard-1",
    100_000,
    ProductCategory(
        name = "standard",
        provider = UCLOUD_PROVIDER,
        accountingFrequency = AccountingFrequency.PERIODIC_MINUTE,
        accountingUnit = AccountingUnit(
            name = "DDK",
            namePlural = "DKK",
            floatingPoint = true,
            displayFrequencySuffix = false
        ),
        productType = ProductType.COMPUTE
    ),
    cpu = 1,
    memoryInGigs = 1,
    description = "product description"
)

val sampleCompute2 = sampleCompute.copy(
    name = "u1-standard-2",
    cpu = 2,
    memoryInGigs = 2,
    price = 200_000
)

val sampleCompute3 = sampleCompute.copy(
    name = "u1-standard-3",
    cpu = 3,
    memoryInGigs = 3,
    price = 300_000
)

val sampleCompute4 = sampleCompute.copy(
    name = "u1-standard-4",
    cpu = 4,
    memoryInGigs = 4,
    price = 400_000
)

val sampleCompute5 = sampleCompute.copy(
    name = "u1-standard-5",
    cpu = 5,
    memoryInGigs = 5,
    price = 500_000
)

val sampleComputeOtherProvider = sampleCompute.copy(
    category = ProductCategory(
        name = "standard",
        provider = OTHER_PROVIDER,
        accountingFrequency = AccountingFrequency.PERIODIC_MINUTE,
        accountingUnit = AccountingUnit(
            name = "DDK",
            namePlural = "DDK",
            floatingPoint = true,
            displayFrequencySuffix = false
        ),
        productType = ProductType.COMPUTE
    )
)

val sampleStorageDifferential = ProductV2.Storage(
    name = "u1-cephfs-quota",
    price = 1L,
    ProductCategory(
        "cephfs-quota",
        UCLOUD_PROVIDER,
        accountingFrequency = AccountingFrequency.ONCE,
        accountingUnit = AccountingUnit(
            "GB",
            "GBs",
            floatingPoint = false,
            displayFrequencySuffix = false
        ),
        productType = ProductType.STORAGE
    ),
    description = "product description",
)

val sampleStorage = ProductV2.Storage(
    "u1-cephfs",
    100_000,
    ProductCategory(
        name = "cephfs",
        provider = UCLOUD_PROVIDER,
        accountingFrequency = AccountingFrequency.PERIODIC_DAY,
        accountingUnit = AccountingUnit(
            "GB",
            "GBs",
            floatingPoint = true,
            displayFrequencySuffix = false
        ),
        productType = ProductType.STORAGE
    ),
    description = "product description"
)

val sampleStorage2 = sampleStorage.copy(
    name = "u2-cephfs"
)

val sampleStorageOtherProvider = sampleStorageDifferential.copy(
    category = ProductCategory(
        name = "cephfs",
        provider = OTHER_PROVIDER,
        accountingFrequency = AccountingFrequency.ONCE,
        accountingUnit = AccountingUnit(
            "GB",
            "GBs",
            floatingPoint = false,
            displayFrequencySuffix = false
        ),
        productType = ProductType.STORAGE
    )
)

val sampleNetworkIp = ProductV2.NetworkIP(
    "u1-public-ip",
    1,
    ProductCategory(
        name = "public-ip",
        provider = UCLOUD_PROVIDER,
        accountingFrequency = AccountingFrequency.ONCE,
        accountingUnit = AccountingUnit(
            "IP",
            "IPs",
            floatingPoint = false,
            displayFrequencySuffix = false
        ),
        productType = ProductType.NETWORK_IP
    ),
    description = "product description"
)

val sampleProducts = listOf(sampleCompute, sampleStorage, sampleIngress, sampleNetworkIp, sampleStorageDifferential,
    sampleCompute2, sampleCompute3, sampleCompute4, sampleCompute5)
val sampleProductsOtherProvider = listOf(sampleComputeOtherProvider, sampleStorageOtherProvider)

suspend fun createProvider(providerName: String = UCLOUD_PROVIDER): RootProjectInitialization {
    val projectId = try {
        Projects.create.call(
            bulkRequestOf(
                Project.Specification(
                    parent = null,
                    title = providerName,
                    false
                )
            ),
            adminClient
        ).orThrow().responses.first().id
    } catch (ex: RPCException) {
        if (ex.httpStatusCode == HttpStatusCode.Conflict) {
            retrieveProviderProjectId()
        } else {
            throw ex
        }
    }

    try {
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
    } catch (ex: RPCException) {
        if (ex.httpStatusCode == HttpStatusCode.Conflict) {
            println("Provider already exists")
        }
        else {
            throw ex
        }
    }

    return RootProjectInitialization(
        adminClient,
        adminUsername,
        projectId
    )

}

/**
 * Creates a sample catalog of products
 */
suspend fun createSampleProducts(): RootProjectInitialization {

    val providerInfo = createProvider()

    for (product in sampleProducts) {
        ProductsV2.create.call(
            BulkRequest(listOf(product)),
            serviceClient
        ).orThrow()
    }

    return providerInfo
}

suspend fun productV1toV2(product: Product): ProductV2 {
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
