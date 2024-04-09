package dk.sdu.cloud.utils

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.*
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.service.Time
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.min

suspend fun userHasResourcesAvailable(job: Job): Boolean {
    return AccountingV2.checkProviderUsable.call(
        bulkRequestOf(
            AccountingV2.CheckProviderUsable.RequestItem(
                job.owner.toWalletOwner(),
                job.specification.product.toCategory(),
            )
        ),
        serviceContext.rpcClient
    ).orThrow().responses.single().maxUsable > 0
}

suspend fun reportJobUsage(job: Job): Boolean {
    val now = Time.now()
    val endOfJob = job.updates.findLast { it.state?.isFinal() == true }?.timestamp
    val nowOrEndOfJob = min(now, endOfJob ?: Long.MAX_VALUE)
    val wallTime = nowOrEndOfJob - (job.status.startedAt ?: nowOrEndOfJob)

    return reportUsage(
        job.owner.toWalletOwner(),
        job.specification.product,
        job.specification.replicas.toLong(),
        wallTime / 60_000L,
        job.id
    )
}

/*
Base units:

- COMPUTE: Number of nodes
- STORAGE: Bytes
- NETWORK_IP: Number of IP addresses
- INGRESS: Number of public links
- LICENSE: Number of licenses
 */

fun convertFromBaseUnitsToProductUnits(
    category: Category,
    product: IndividualProduct<*>,
    baseUnits: Long
): Long {
    val unit = when (val cost = category.cost) {
        ProductCost.Free -> return 0
        is ProductCost.Money -> cost.unit
        is ProductCost.Resource -> cost.unit
    }

    return when (category.type) {
        ProductType.STORAGE -> {
            val actualUnit = StorageUnit.valueOf(unit ?: "GB")
            actualUnit.convertToThisUnitFromBytes(baseUnits)
        }
        ProductType.COMPUTE -> {
            val actualUnit = ComputeResourceType.valueOf(unit ?: "Cpu")
            val computeProduct = product.spec as IndividualProduct.ProductSpec.Compute
            val factor = computeProduct.getResource(actualUnit)

            return baseUnits * factor
        }
        ProductType.INGRESS -> baseUnits
        ProductType.LICENSE -> baseUnits
        ProductType.NETWORK_IP -> baseUnits
    }
}

suspend fun reportUsage(
    workspace: WalletOwner,
    product: ProductReferenceV2,
    usageInBaseUnits: Long,
    minutesUsed: Long?,
    scope: String? = null,
): Boolean {
    checkServerMode()

    val (category, cfgProduct) = loadedConfig.products.findCategoryAndProduct(product)
        ?: return false

    val usageInProductUnits = convertFromBaseUnitsToProductUnits(category, cfgProduct, usageInBaseUnits)

    val cost = category.cost

    var timeFactor = 1L

    val productTimeInterval = when (cost) {
        ProductCost.Free -> ProductCost.AccountingInterval.Daily
        is ProductCost.Money -> cost.interval
        is ProductCost.Resource -> cost.accountingInterval
    }

    if (productTimeInterval != null) {
        if (minutesUsed == null) {
            error("Unable to perform accounting on this product: $product." +
                    "This plugin was unable to resolve for how long it was used.")
        }

        val millisUsed = ProductCost.AccountingInterval.Minutely.toMillis(minutesUsed)
        timeFactor = productTimeInterval.convertFromMillis(millisUsed).wholePart
    }

    var balanceUsed = usageInProductUnits
    if (cost is ProductCost.Money) balanceUsed *= cfgProduct.price
    balanceUsed *= timeFactor

    return AccountingV2.reportUsage.call(
        bulkRequestOf(
            UsageReportItem(
                isDeltaCharge = false,
                workspace,
                category.category.toV2Id(),
                balanceUsed,
                ChargeDescription(scope),
            )
        ),
        serviceContext.rpcClient,
    ).orThrow().responses.single()
}

suspend fun reportRawUsage(
    workspace: WalletOwner,
    category: ProductCategoryIdV2,
    usageInUCloudUnits: Long,
    scope: String? = null,
): Boolean {
    checkServerMode()
    return AccountingV2.reportUsage.call(
        bulkRequestOf(
            UsageReportItem(
                isDeltaCharge = false,
                workspace,
                category,
                usageInUCloudUnits,
                ChargeDescription(scope),
            )
        ),
        serviceContext.rpcClient,
    ).orThrow().responses.single()
}

// Utilities
// ====================================================================================================================
private fun checkServerMode() {
    require(loadedConfig.shouldRunServerCode()) { "This function can only be run in server mode" }
}

@Suppress("DEPRECATION")
private fun ProductReference.toCategory(): ProductCategoryIdV2 {
    return ProductCategoryIdV2(category, provider)
}

@Suppress("DEPRECATION", "TYPEALIAS_EXPANSION_DEPRECATION")
fun ProductReference.toV2(): ProductReferenceV2 {
    return ProductReferenceV2(id, category, provider)
}

fun ProductCategory.toV2Id(): ProductCategoryIdV2 {
    return ProductCategoryIdV2(name, provider)
}

fun walletOwnerFromOwnerString(owner: String): WalletOwner =
    if (owner.matches(PROJECT_REGEX)) WalletOwner.Project(owner)
    else WalletOwner.User(owner)

fun ResourceOwner.toSimpleString() = project ?: createdBy
fun ResourceOwner.toReference() = project ?: createdBy
fun ResourceOwner.toWalletOwner(): WalletOwner {
    return if (project != null) WalletOwner.Project(project!!)
    else WalletOwner.User(createdBy)
}

val PROJECT_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
