package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.config.*
import dk.sdu.cloud.accounting.api.*
import kotlin.math.floor
import kotlin.math.max

class ProductEstimator(
    private val config: VerifiedConfig,
) {
    private val plugins = config.plugins.jobs.values.filterIsInstance<SlurmPlugin>()
    private val allProducts = plugins.flatMap { it.productAllocationResolved.filterIsInstance<ProductV2.Compute>() }

    data class EstimatedProduct(
        val product: ProductReference,
        val replicas: Int,
        val account: AccountMapper.UCloudKey,
    )

    suspend fun estimateProduct(
        row: SlurmCommandLine.SlurmAccountingRow, 
        relevantUCloudAccounts: List<AccountMapper.UCloudKey>
    ): EstimatedProduct? {
        val sortedAccounts = relevantUCloudAccounts.sortedWith(
            compareBy<AccountMapper.UCloudKey> { it.owner.createdBy }
                .thenBy { it.owner.project }
        )

        val productsInCategories = allProducts.filter { product ->
            relevantUCloudAccounts.any { acc -> acc.productCategory == product.category.name }
        }

        val perfectMatch = productsInCategories.find { product ->
            product.cpu == row.cpusRequested &&
                (product.memoryInGigs ?: 0) == (row.memoryRequestedInMegs / 1000).toInt() &&
                (
                    (product.gpu == null && row.gpu == 0) ||
                    (product.gpu == row.gpu)
                )
        }

        if (perfectMatch != null) {
            return EstimatedProduct(
                perfectMatch.toReference(), 
                row.nodesRequested, 
                sortedAccounts.find { it.productCategory == perfectMatch.category.name }
                    ?: error("Corrupt state in product estimator")
            )
        }

        val estimation = productsInCategories
            .filter { product ->
                row.cpusRequested <= (product.cpu ?: 0) &&
                    (row.memoryRequestedInMegs / 1000) <= (product.memoryInGigs ?: 0) &&
                    row.gpu <= (row.gpu ?: 0)
            }
            .sortedWith(
                compareBy<ProductV2.Compute> { (it.cpu ?: 0) + (it.gpu ?: 0) }
                    .thenBy { (it.memoryInGigs ?: 0) }
                    .thenBy { "${it.name}/${it.category.name}" }
            )
            .firstOrNull() ?: return null

        val estimationMultiplier = row.cpusRequested.toDouble() / (estimation.cpu ?: 1)
        return EstimatedProduct(
            estimation.toReference(),
            max(1, floor(estimationMultiplier * row.nodesRequested).toInt()),
            sortedAccounts.find { it.productCategory == estimation.category.name }
                ?: error("Corrupt state in product estimator")
        )
    }

    private fun Product.toReference(): ProductReference =
        ProductReference(name, category.name, category.provider)
}
