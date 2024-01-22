package dk.sdu.cloud.utils

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.ComputeResourceType
import dk.sdu.cloud.config.IndividualProduct
import dk.sdu.cloud.config.ProductCost
import dk.sdu.cloud.config.StorageUnit
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.provider.api.Resource
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.MathContext

/*
API function structure for reporting of usage.

1. "report"

2. Amount
   - DeltaUse:      An incremental usage report. Do not use if managed externally. Examples of this include:

                    - a resource has used an additional 10 minutes of compute time
                    - a resource has used an additional 3GB of storage
                    - a resource has used an additional -3GB of storage

   - ConcurrentUse: Current total (concurrent) use, will consider the period granularity in the charge. Do not use if
                    managed by an external system. Examples of this function include:

                    - a workspace is currently using 10GB of storage.
                    - a workspace is currently using 30 vCPU of compute
                    - a workspace currently has 10 active licenses
                    - a workspace currently has 0 IP addresses

   - Balance:       Sets the balance directly and ignores period granularity from the charge.
                    Use if managed by an external system. Examples of this function include:

                    - a workspace has used 100 DKK of their quota
                    - a workspace has used 300 GB of their quota
                    - a workspace has used 1000 GB-hours of their quota
                    - a workspace has used 10 IP addresses of their quota
                    - a workspace has used 2 IP addresses of their quota

3. [ResourceType]:
 */

fun isReportDeltaUsePossible(product: ProductReferenceV2): Boolean = true
fun isReportBalancePossible(category: ProductCategoryIdV2): Boolean = true
fun isReportConcurrentUsePossible(category: ProductCategoryIdV2): Boolean {
    val resolvedCategory = loadedConfig.products.findCategory(category.name) ?: return false
    val cost = resolvedCategory.cost
    return cost !is ProductCost.Money || cost.interval == null
}

private val resourceTrackedTimeMutex = Mutex()
private val resourceLastCall = HashMap<Long, Long>()
private val resourceTrackedTime = HashMap<Long, Long>()

private data class WorkspaceKey(val workspace: WalletOwner, val category: ProductCategoryIdV2)
private val workspaceTrackedTimeMutex = Mutex()
private val workspaceLastCall = HashMap<WorkspaceKey, Long>()
private val workspaceTrackedTime = HashMap<WorkspaceKey, Long>()

suspend fun initAccountingSystem() {
    if (!loadedConfig.shouldRunServerCode()) return
    val log = Logger("Accounting")

    dbConnection.withSession { session ->
        val earliestCall = Time.now() - (1000 * 60 * 60 * 24 * 14L)
        session.prepareStatement(
            """
                select resource_id, time_tracked
                from accounting_tracked_resource
                where last_call >= :earliest_call
            """
        ).useAndInvoke(
            prepare = { bindLong("earliest_call", earliestCall) },
            readRow = { row ->
                val id = row.getLong(0)!!
                val timeTracked = row.getLong(1)!!
                resourceTrackedTime[id] = timeTracked
            }
        )

        session.prepareStatement(
            """
                select workspace_reference, workspace_is_user, category, time_tracked
                from accounting_tracked_workspace
                where last_call >= :earliest_call
            """
        ).useAndInvoke(
            prepare = { bindLong("earliest_call", earliestCall) },
            readRow = { row ->
                val ref = row.getString(0)!!
                val isUser = row.getBoolean(1)!!
                val category = row.getString(2)!!
                val timeTracked = row.getLong(3)!!
                val key = WorkspaceKey(
                    if (isUser) WalletOwner.User(ref) else WalletOwner.Project(ref),
                    ProductCategoryIdV2(category, providerId),
                )
                workspaceTrackedTime[key] = timeTracked
            }
        )
    }

    var lastSync = 0L
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking { synchronizeAccountingState(lastSync) }
    })

    ProcessingScope.launch {
        while (isActive) {
            try {
                val startOfBlock = Time.now()
                synchronizeAccountingState(lastSync)
                lastSync = startOfBlock

                delay(60_000 - (Time.now() - startOfBlock))
            } catch (ex: Throwable) {
                log.warn("Caught exception while synchronizing accounting state:\n${ex.toReadableStacktrace()}")
            }
        }
    }
}

private suspend fun trackResourceTimeUsageAndConvertForReportingWorkspace(
    workspace: WalletOwner,
    category: ProductCategoryIdV2,
    desiredInterval: ProductCost.AccountingInterval,
    intervalInMilliseconds: Long? = null,
): Long {
    val key = WorkspaceKey(workspace, category)
    val now = Time.now()
    workspaceTrackedTimeMutex.withLock {
        val timeElapsed = if (intervalInMilliseconds == null) {
            val lastCall = workspaceLastCall.getOrPut(key) { now }
            now - lastCall
        } else {
            intervalInMilliseconds
        }

        val newTracked = (workspaceTrackedTime[key] ?: 0) + timeElapsed
        workspaceTrackedTime[key] = newTracked

        val (whole, remaining) = desiredInterval.convertFromMillis(newTracked)
        workspaceTrackedTime[key] = remaining
        workspaceLastCall[key] = now

        return whole
    }
}

private suspend fun trackResourceTimeUsageAndConvertForReporting(
    resource: Resource<*, *>,
    desiredInterval: ProductCost.AccountingInterval,
    intervalInMilliseconds: Long? = null,
): Long {
    val id = resource.id.toLongOrNull() ?: error("UCloud has changed ID format?")
    val now = Time.now()

    resourceTrackedTimeMutex.withLock {
        val timeElapsed = if (intervalInMilliseconds == null) {
            val lastCall = resourceLastCall.getOrPut(id) { now }
            now - lastCall
        } else {
            intervalInMilliseconds
        }

        val newTracked = (resourceTrackedTime[id] ?: 0) + timeElapsed
        resourceTrackedTime[id] = newTracked

        val (whole, remaining) = desiredInterval.convertFromMillis(newTracked)
        resourceTrackedTime[id] = remaining
        resourceLastCall[id] = now

        return whole
    }
}

suspend fun userHasResourcesAvailable(job: Job): Boolean {
    return Accounting.check.call(
        BulkRequest(
            listOf(
                ChargeWalletRequestItem(
                    payer = job.owner.toWalletOwner(),
                    units = 1,
                    periods = 1,
                    product = job.specification.product,
                    performedBy = job.owner.createdBy,
                    description = "Checking if resources exists"
                )
            )
        ),
        serviceContext.rpcClient
    ).orThrow().responses.firstOrNull() ?: false
}

/**
 * Reports delta usage for a compute job.
 *
 * This function is capable of automatically tracking how long the job has been alive, in this case the plugin
 * simply needs to pass `null` for the [intervalInMilliseconds] parameter.
 *
 * This API can only be used in server mode.
 *
 * @return `true` if the job is allowed to continue, otherwise `false`
 */
suspend fun reportDeltaUseCompute(job: Job, intervalInMilliseconds: Long? = null): Boolean {
    val (category, product) = loadedConfig.products.findCategoryAndProduct(job.specification.product.toV2())
        ?: return false

    var resourceCount = job.specification.replicas.toLong()
    val cost = category.cost
    if (cost is ProductCost.WithUnit && cost.unit != null) {
        val cpuResource = ComputeResourceType.valueOf(cost.unit!!)
        val cpuSpec = product.spec as IndividualProduct.ProductSpec.Compute
        resourceCount *= cpuSpec.getResource(cpuResource)
    }

    return reportDeltaUse(job, intervalInMilliseconds, resourceCount)
}

suspend fun reportDeltaUse(
    resource: Resource<*, *>,
    intervalInMilliseconds: Long? = null,
    amountUsed: Long = 1,
    description: ChargeDescription = ChargeDescription("Incremental usage of resource ${resource.id}", emptyList())
): Boolean {
    checkServerMode()
    val categoryAndProduct = loadedConfig.products.findCategoryAndProduct(resource.specification.product.toV2())
        ?: return false

    val (category, product) = categoryAndProduct
    val cost = category.cost
    val price = product.price

    val balanceUsed = when (cost) {
        ProductCost.Free -> 0

        is ProductCost.Money -> {
            val interval = cost.interval
            if (interval != null) {
                val wholeMinutes = trackResourceTimeUsageAndConvertForReporting(
                    resource,
                    ProductCost.AccountingInterval.Minutely,
                    intervalInMilliseconds,
                )

                val mc = MathContext.DECIMAL128
                val pricePerMinute = BigDecimal(1).divide(BigDecimal(interval.minutes), mc).multiply(BigDecimal(price), mc)

                pricePerMinute.multiply(BigDecimal(wholeMinutes), mc).multiply(BigDecimal(amountUsed), mc).toLong()
            } else {
                amountUsed
            }
        }

        is ProductCost.Resource -> {
            if (cost.accountingInterval != null) {
                trackResourceTimeUsageAndConvertForReporting(resource, cost.accountingInterval, intervalInMilliseconds) * amountUsed
            } else {
                amountUsed
            }
        }
    }

    if (balanceUsed == 0L) return true

    return AccountingV2.reportDelta.call(
        bulkRequestOf(
            DeltaReportItem(
                resource.owner.toWalletOwner(),
                resource.specification.product.toCategory(),
                balanceUsed,
                description,
            )
        ),
        serviceContext.rpcClient
    ).orThrow().responses.single()
}

suspend fun reportConcurrentUseStorage(
    workspace: WalletOwner,
    category: ProductCategoryIdV2,
    bytesUsed: Long,
    intervalInMilliseconds: Long? = null,
    description: ChargeDescription = ChargeDescription("Periodic usage of resource", emptyList())
): Boolean {
    val productCategory = loadedConfig.products.findCategory(category.name) ?: return false
    val cost = productCategory.cost
    val unit = StorageUnit.valueOf((cost as? ProductCost.WithUnit)?.unit ?: "GB")
    val wholeUnitsUsed = unit.convertToThisUnitFromBytes(bytesUsed)
    return reportConcurrentUse(workspace, category, wholeUnitsUsed, intervalInMilliseconds, description)
}

suspend fun reportConcurrentUse(
    workspace: WalletOwner,
    category: ProductCategoryIdV2,
    amountUsed: Long,
    intervalInMilliseconds: Long? = null,
    description: ChargeDescription = ChargeDescription("Periodic usage of resource", emptyList())
): Boolean {
    checkServerMode()

    // NOTE(Dan): This endpoint is used to report the total combined usage at the moment. This goes down one of two
    // paths, if this is a periodic charge then we want to determine the period size and multiply it by the amount
    // used. In the end, this will trigger a delta charge. On the other hand, if this is not a periodic charge, then
    // we simply set the balance in UCloud since this now just corresponds to our current usage.
    val resolvedCategory = loadedConfig.products.findCategory(category.name) ?: return false
    val cost = resolvedCategory.cost
    val interval: ProductCost.AccountingInterval? = when (cost) {
        ProductCost.Free -> null
        is ProductCost.Money -> cost.interval
        is ProductCost.Resource -> cost.accountingInterval
    }

    if (interval == null) return reportBalance(workspace, category, amountUsed)

    @Suppress("KotlinConstantConditions")
    val balance = when (cost) {
        is ProductCost.Money -> {
            error("The reportTotalUse endpoint cannot be used with periodic money charges. " +
                    "Plugin should have rejected this code earlier or used a different method if " +
                    "this is being tracked by an external system (we do not know the price of the products).")
        }

        is ProductCost.Resource -> {
            val intervalCount = trackResourceTimeUsageAndConvertForReportingWorkspace(workspace, category, interval,
                intervalInMilliseconds)

            intervalCount * amountUsed
        }

        ProductCost.Free -> error("Should not happen (removed by previous branch?)")
    }

    return AccountingV2.reportDelta.call(
        bulkRequestOf(
            DeltaReportItem(
                workspace,
                category,
                balance,
                description
            )
        ),
        serviceContext.rpcClient
    ).orThrow().responses.single()
}

suspend fun reportBalance(
    workspace: WalletOwner,
    category: ProductCategoryIdV2,
    newUsage: Long,
    description: ChargeDescription = ChargeDescription("Synchronization of usage", emptyList())
): Boolean {
    checkServerMode()
    return AccountingV2.reportTotalUsage.call(
        bulkRequestOf(
            DeltaReportItem(
                workspace,
                category,
                newUsage,
                description,
            ),
        ),
        serviceContext.rpcClient
    ).orThrow().responses.single()
}

// Utilities
// ====================================================================================================================
private fun checkServerMode() {
    require(loadedConfig.shouldRunServerCode()) { "This function can only be run in server mode" }
}

private fun ResourceOwner.toWalletOwner(): WalletOwner {
    return if (project != null) WalletOwner.Project(project!!)
    else WalletOwner.User(createdBy)
}

@Suppress("DEPRECATION")
private fun ProductReference.toCategory(): ProductCategoryIdV2 {
    return ProductCategoryIdV2(category, provider)
}

@Suppress("DEPRECATION")
fun ProductReference.toV2(): ProductReferenceV2 {
    return ProductReferenceV2(id, category, provider)
}

@Suppress("DEPRECATION")
fun ProductCategory.toV2Id(): ProductCategoryIdV2 {
    return ProductCategoryIdV2(name, provider)
}

// Persistent storage
// ====================================================================================================================
private suspend fun synchronizeAccountingState(lastSync: Long) {
    workspaceTrackedTimeMutex.withLock {
        val keysToTrack = workspaceLastCall.entries.mapNotNull { if (it.value < lastSync) null else it.key }

        val workspaceRefs = ArrayList<String>()
        val workspaceIsUser = ArrayList<Boolean>()
        val categories = ArrayList<String>()
        val times = ArrayList<Long>()
        val lastCall = ArrayList<Long>()

        for (key in keysToTrack) {
            val time = workspaceTrackedTime[key] ?: continue
            workspaceRefs.add(when (key.workspace) {
                is WalletOwner.Project -> key.workspace.projectId
                is WalletOwner.User -> key.workspace.username
            })
            workspaceIsUser.add(when (key.workspace) {
                is WalletOwner.Project -> false
                is WalletOwner.User -> true
            })
            categories.add(key.category.name)
            times.add(time)
            lastCall.add(workspaceLastCall[key] ?: 0L)
        }

        if (times.isNotEmpty()) {
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        with data as (
                            select unnest(:refs) refs, unnest(:is_user) is_user, 
                                   unnest(:categories) categories, unnest(:times) times, 
                                   unnest(:last_call) last_call
                        )
                        insert into accounting_tracked_workspace
                            (workspace_reference, workspace_is_user, category, time_tracked, last_call)
                        select refs, is_user, categories, times, last_call
                        from data
                        on conflict (workspace_reference, workspace_is_user, category) do update set
                            last_call = excluded.last_call,
                            time_tracked = excluded.time_tracked
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindList("refs", workspaceRefs, SQL_TYPE_HINT_TEXT)
                        bindList("is_user", workspaceIsUser, SQL_TYPE_HINT_BOOL)
                        bindList("categories", categories, SQL_TYPE_HINT_TEXT)
                        bindList("times", times, SQL_TYPE_HINT_INT8)
                        bindList("last_call", lastCall, SQL_TYPE_HINT_INT8)
                    }
                )
            }
        }
    }

    resourceTrackedTimeMutex.withLock {
        val keysToTrack = resourceLastCall.entries.mapNotNull { if (it.value < lastSync) null else it.key }

        val ids = ArrayList<Long>()
        val times = ArrayList<Long>()
        val lastCall = ArrayList<Long>()

        for (key in keysToTrack) {
            val time = resourceTrackedTime[key] ?: continue
            ids.add(key)
            times.add(time)
            lastCall.add(resourceLastCall[key] ?: 0L)
        }

        if (times.isNotEmpty()) {
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        with data as (
                            select unnest(:ids) ids, unnest(:times) times, unnest(:last_call) last_call
                        )
                        insert into accounting_tracked_resource
                            (resource_id, time_tracked, last_call)
                        select ids, times, last_call
                        from data
                        on conflict (resource_id) do update set
                            time_tracked = excluded.time_tracked,
                            last_call = excluded.last_call
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindList("ids", ids, SQL_TYPE_HINT_INT8)
                        bindList("times", times, SQL_TYPE_HINT_INT8)
                        bindList("last_call", lastCall, SQL_TYPE_HINT_INT8)
                    }
                )
            }
        }
    }
}

fun walletOwnerFromOwnerString(owner: String): WalletOwner =
    if (owner.matches(PROJECT_REGEX)) WalletOwner.Project(owner)
    else WalletOwner.User(owner)

fun ResourceOwner.toSimpleString() = project ?: createdBy

val PROJECT_REGEX =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
