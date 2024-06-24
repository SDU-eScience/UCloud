package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.api.AllocationGroup
import dk.sdu.cloud.accounting.api.ProductCategory
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.calls.client.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

internal val didLoadUnsynchronizedGrants = AtomicBoolean(false)

internal val ownersByReference = HashMap<String, InternalOwner>()
internal val ownersById = HashMap<Int, InternalOwner>()
internal val ownersIdAccumulator = AtomicInteger(1)

internal val allocations = HashMap<Int, InternalAllocation>()
internal val allocationsIdAccumulator = AtomicInteger(1)

internal val walletsById = HashMap<Int, InternalWallet>()
internal val walletsByOwner = HashMap<Int, ArrayList<InternalWallet>>()
internal val walletsIdAccumulator = AtomicInteger(1)

internal val allocationGroups = HashMap<Int, InternalAllocationGroup>()
internal val allocationGroupIdAccumulator = AtomicInteger(1)

internal val scopedUsage = HashMap<String, Long>()
internal val scopedDirty = HashMap<String, Boolean>()

internal val mostRecentSignificantUpdateByProvider = HashMap<String, Long>()

data class InternalOwner(val id: Int, val reference: String, var dirty: Boolean) {
    fun isProject(): Boolean = reference.matches(PROJECT_REGEX)
    fun toWalletOwner(): WalletOwner =
        if (isProject()) WalletOwner.Project(reference)
        else WalletOwner.User(reference)

    companion object {
        val PROJECT_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}

data class InternalAllocation(
    val id: Int,
    val belongsToWallet: Int,
    val parentWallet: Int,
    var quota: Long,
    var start: Long,
    var end: Long,
    var retired: Boolean,
    var retiredUsage: Long = 0L,
    val grantedIn: Long?,
    var isDirty: Boolean,
    var committed: Boolean = false
) {
    fun isActive(now: Long): Boolean {
        return !retired && commited && now >= start
    }

    fun preferredBalance(now: Long): Long {
        if (retired) return 0L
        if (now <= start) return 0L
        if (now >= end) return quota

        // TODO Potential for integer overflow here. Might be safer to do some floating point math here.
        return (quota * (now - start)) / (end - start)
    }
}

data class InternalAllocationGroup(
    //Group ID
    val id: Int,
    //In which wallet does this group belong to
    val associatedWallet: Int,
    //Which wallet has created the allocations in this group
    val parentWallet: Int,
    var treeUsage: Long,
    var retiredTreeUsage: Long,
    var earliestExpiration: Long,
    var allocationSet: HashMap<Int, Boolean>,
    var isDirty: Boolean
) {
    fun isActive(): Boolean {
        return allocationSet.any { it.value }
    }

    fun totalActiveQuota(): Long {
        var sum = 0L
        for ((id, isActive) in allocationSet) {
            if (!isActive) continue
            sum += allocations.getValue(id).quota
        }
        return sum
    }

    fun preferredBalance(now: Long): Long {
        var result = 0L
        for ((id, isActive) in allocationSet) {
            if (!isActive) continue
            val alloc = allocations.getValue(id)
            result += alloc.preferredBalance(now)
        }
        return result
    }

    fun toApi(): AllocationGroup {
        return AllocationGroup(
            id = id,
            allocationSet.keys.map { id ->
                val alloc = allocations.getValue(id)
                AllocationGroup.Alloc(
                    id.toLong(),
                    alloc.start,
                    alloc.end,
                    alloc.quota,
                    alloc.grantedIn,
                    alloc.retiredUsage.takeIf { alloc.retired },
                )
            },
            treeUsage,
        )
    }
}

data class InternalWallet(
    val id: Int,
    val ownedBy: Int,
    val category: ProductCategory,
    var localUsage: Long,
    //Mapping between parent wallet ID and the allocations given
    val allocationsByParent: HashMap<Int, InternalAllocationGroup>,
    //Mapping between child wallet ID and the tree usage of the allocation group given by this wallet
    val childrenUsage: HashMap<Int, Long>,
    var localRetiredUsage: Long,
    //Mapping between chile wallet ID and the retired tree usage of the allocation group given by this wallet
    val childrenRetiredUsage: HashMap<Int, Long>,
    var excessUsage: Long,
    var totalAllocated: Long,
    var totalRetiredAllocated: Long,
    var isDirty: Boolean,
    var wasLocked: Boolean,
    var lastSignificantUpdateAt: Long,
) {
    fun parentEdgeCost(parentId: Int, now: Long): Long {
        val group = allocationsByParent.getValue(parentId)
        val preferredBalance = if (!category.isNotCapacityBased()) {
            group.totalActiveQuota()
        } else {
            group.preferredBalance(now)
        }

        val balanceFactor = (group.treeUsage - preferredBalance) * BALANCE_WEIGHT
        val timeFactor = (group.earliestExpiration - now) * TIME_WEIGHT
        return balanceFactor * timeFactor
    }

    fun totalUsage(): Long {
        var totalUsage = localUsage
        for ((_, childUse) in childrenUsage) {
            totalUsage += childUse
        }

        if (!category.isCapacityBased()) {
            for ((_, childUse) in childrenRetiredUsage) {
                totalUsage += childUse
            }
        }
        return totalUsage
    }

    fun totalActiveQuota(): Long {
        var result = 0L
        for ((_, group) in allocationsByParent) {
            result += group.totalActiveQuota()
        }
        return result
    }

    fun isOverspending(): Boolean {
        if (excessUsage > 0) return true
        if (totalUsage() > totalActiveQuota()) return true
        return false
    }

    fun isCapacityBased(): Boolean = category.isCapacityBased()
    fun totalTreeUsage(): Long {
        var res = 0L
        for ((_, ag) in allocationsByParent) {
            res += ag.treeUsage
        }
        return res
    }

    fun treeUsage(parentId: Int): Long {
        var totalUsage = allocationsByParent[parentId]?.treeUsage ?: 0L
        if (!isCapacityBased()) {
            totalUsage += allocationsByParent[parentId]?.retiredTreeUsage ?: 0L
        }
        return totalUsage
    }

    fun generateLocalWallets(): List<Int> {
        val result = ArrayList<Int>()
        result.add(id)

        val queue = ArrayDeque<Int>()
        queue.add(id)

        while (queue.isNotEmpty()) {
            val currentWalletId = queue.removeFirst()
            val currentWallet = walletsById.getValue(currentWalletId)

            for ((pId) in currentWallet.allocationsByParent) {
                if (pId == 0) continue
                if (result.contains(pId)) continue
                result.add(pId)
                queue.add(pId)
            }
        }

        return result
    }

    companion object {
        private const val BALANCE_WEIGHT = 1L shl 25
        private const val TIME_WEIGHT = 1
    }
}
