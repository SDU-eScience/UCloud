package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.accounting.api.ProductCategory
import dk.sdu.cloud.calls.client.AtomicInteger

internal val ownersByReference = HashMap<String, InternalOwner>()
internal val ownersById = HashMap<Int, InternalOwner>()
internal val ownersIdAccumulator = AtomicInteger(1)

internal val allocations = HashMap<Int, InternalAllocation>()
internal val allocationsIdAccumulator = AtomicInteger(1)

internal val walletsById = HashMap<Int, InternalWallet>()
internal val walletsByOwner = HashMap<Int, ArrayList<InternalWallet>>()
internal val walletsIdAccumulator = AtomicInteger(1)

data class InternalOwner(val id: Int, val reference: String) {
    fun isProject(): Boolean = reference.matches(PROJECT_REGEX)

    companion object {
        val PROJECT_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}

data class InternalAllocation(
    val id: Int,
    val belongsTo: Int,
    val parentWallet: Int,
    var quota: Long,
    var start: Long,
    var end: Long,
    var retired: Boolean,
    var retiredUsage: Long = 0L,
) {
    fun isActive(now: Long): Boolean {
        return !retired && now >= start
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
    var treeUsage: Long,
    var retiredTreeUsage: Long,
    var earliestExpiration: Long,
    var allocationSet: HashMap<Int, Boolean>,
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
}

data class InternalWallet(
    val id: Int,
    val ownedBy: Int,
    val category: ProductCategory,
    var localUsage: Long,
    val allocationsByParent: HashMap<Int, InternalAllocationGroup>,
    val childrenUsage: HashMap<Int, Long>,
    var localRetiredUsage: Long,
    val childrenRetiredUsage: HashMap<Int, Long>,
    var localOverspending: Long,
    var totalAllocated: Long,
    var totalRetiredAllocated: Long,
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
        var totalUsage = localUsage + localOverspending
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
        if (localOverspending > 0) return true
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
