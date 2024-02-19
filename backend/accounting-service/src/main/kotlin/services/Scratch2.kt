package dk.sdu.cloud.accounting.services.fifgjgigiugi

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Wallet(
    val id: Int,
    val allocationIds: IntArray,

    var localUsage: Long = 0L,
    private var realTreeUsageByChildren: HashMap<Int, Long> = HashMap(),
) {
    fun incrementTreeUsage(childId: Int, amount: Long) {
        realTreeUsageByChildren[childId] = (realTreeUsageByChildren[childId] ?: 0L) + amount
    }

    fun realUsageFromChild(childId: Int): Long {
        return realTreeUsageByChildren[childId] ?: 0L
    }

    fun isRootWallet() = allocationIds.any { allocations[it].parentWallet == null }

    fun quota() = allocationIds.sumOf {
        val alloc = allocations[it]
        if (alloc.isActiveNow()) alloc.quota else 0
    }

    fun children(): List<Wallet> {
        return allocations.filter { it.parentWallet == id }.map { it.belongsTo }.toSet().map { wallets[it] }
    }

    fun quotaFromParent(parentId: Int): Long {
        return quotasByParent()[parentId] ?: 0L
    }

    fun quotasByParent(): Map<Int?, Long> {
        return allocationIds
            .map { id -> allocations[id] }
            .filter { it.isActiveNow() }
            .groupBy { it.parentWallet }
            .mapValues { (_, allocs) -> allocs.sumOf { it.quota } }
    }

    fun hasResources(): Boolean {
        val allChildren = children()
        val cappedUsage = localUsage + allChildren.sumOf {
            min(it.quotaFromParent(id), realUsageFromChild(it.id))
        }

        return cappedUsage <= quota()
    }
}

class Allocation(
    val belongsTo: Int,
    val parentWallet: Int?,
    var quota: Long,
    var expired: Boolean = false,
    var retired: Boolean = false,
    var retiredUsage: Long = 0L,
) {
    fun isActiveNow() = !expired
}

fun retireAllocation(allocationId: Int, isQuotaBased: Boolean) {
    val allocation = allocations[allocationId]
    val wallet = wallets[allocation.belongsTo]
    val parentWallet = allocation.parentWallet?.let { wallets[it] }

    require(allocation.expired && !allocation.retired)
    if (isQuotaBased) TODO()

    allocation.retired = true

    if (parentWallet != null) {
        val ourTreeUsage2 = parentWallet.realUsageFromChild(wallet.id)
        allocation.retiredUsage = max(allocation.quota, ourTreeUsage2)

        if (wallet.localUsage > 0) {
            val toSubtractFromLocal = min(wallet.localUsage, allocation.retiredUsage)
            wallet.localUsage -= toSubtractFromLocal
            allocation.retiredUsage += toSubtractFromLocal
        }
    }
}

/*
fun maxUsable(
    wallets: Array<Wallet>,
    allocations: Array<Allocation>,
    walletId: Int,
    currentMinimum: Long = Long.MAX_VALUE
): Long {
    val wallet = wallets[walletId]

    var maxUsable = 0L
    for (allocId in wallet.allocations) {
        val allocation = allocations[allocId]
        val capacity = allocation.quota - allocation.treeUsage
        if (!allocation.hasResources()) continue

        val maxFromParent = if (allocation.parentWallet == null) {
            min(currentMinimum, capacity)
        } else {
            min(
                capacity, maxUsable(
                    wallets,
                    allocations,
                    allocation.parentWallet,
                    min(currentMinimum, capacity)
                )
            )
        }

        allocation.treeUsage += maxFromParent
        maxUsable += maxFromParent
    }

    return maxUsable
}

fun proposeSplit2(validAllocations: List<Allocation>, amount: Long): List<Long> {
    require(validAllocations.isNotEmpty())

    val factor = if (amount < 0) -1 else 1
    var remaining = abs(amount)
    val result = ArrayList<Long>()
    for (alloc in validAllocations) {
        val capacity = max(0, alloc.quota)
        val toSubtract = min(remaining, capacity)
        result.add(toSubtract * factor)
        remaining -= toSubtract
    }

    if (remaining > 0) {
        result[0] += remaining * factor
    }

    return result
}
*/

// Proposes a split of usage amongst the allocations.
//
// The returned list must sum to the full amount and must have the same amount
// of elements.
//
// If we must overspend, then at most one allocation can overspend.
//
// If there is a way not to overspend (locally), then we must not overspend (locally).
fun proposeSplit(child: Wallet, uniqueParents: List<Wallet>, amount: Long): List<Long> {
    val byParent = child.quotasByParent()

    val factor = if (amount < 0) -1 else 1
    var remaining = abs(amount)
    val result = ArrayList<Long>()
    val orderedList = if (amount < 0) uniqueParents.reversed() else uniqueParents
    for (parent in orderedList) {
        val parentTreeUsage = parent.realUsageFromChild(child.id)
        val capacity =
            if (amount < 0) parentTreeUsage
            else max(0, byParent.getValue(parent.id) - parentTreeUsage)
        val toSubtract = min(remaining, capacity)
        result.add(toSubtract * factor)
        remaining -= toSubtract
    }

    if (remaining > 0) {
        result[0] += remaining * factor
    }

    return if (amount < 0) result.reversed() else result
}

fun chargeDelta2(
    walletId: Int,
    amount: Long,
    leaf: Boolean = true,
): Boolean {
    // Wallet is not monotonically increasing in usage
    if (amount == 0L) return true

    val wallet = wallets[walletId]

    val parents = wallet.allocationIds
        .map { allocations[it].parentWallet?.let { wallets[it] } }
        .filterNotNull()
        .toSet()
        .toList()

    var anyFailure = false

    if (leaf) wallet.localUsage += amount

    if (wallet.isRootWallet()) {
        if (!wallet.hasResources()) anyFailure = true
    } else {
        if (parents.isNotEmpty()) {
            val split = proposeSplit(wallet, parents, amount)

            for ((parentWallet, splitAmount) in parents.zip(split)) {
                parentWallet.incrementTreeUsage(wallet.id, splitAmount)

                val localSuccess = wallet.hasResources()
                val parentSuccess = chargeDelta2(
                    parentWallet.id,
                    splitAmount,
                    leaf = false
                )

                if (!parentSuccess || !localSuccess) anyFailure = true
            }
        }
    }
    return !anyFailure
}

/*
fun chargeDelta(walletId: Int, amount: Long, leaf: Boolean = true): Boolean {
    // Wallet is monotonically increasing in usage
    // Amount is a positive integer
    if (amount == 0L) return true
    require(amount > 0)

    val wallet = wallets[walletId]
    val validAllocations = wallet.allocations
        .map { allocations[it] }
        .filter { it.isActiveNow() && it.hasResources() }

    if (validAllocations.isEmpty()) return false
    val split = proposeSplit(validAllocations, amount)

    var anyFailure = false
    for ((alloc, splitAmount) in validAllocations.zip(split)) {
        if (alloc.parentWallet != null) {
            val maxToSpend = alloc.quota - alloc.treeUsage

            if (leaf) alloc.localUsage += splitAmount
            alloc.treeUsage += splitAmount

            val localSuccess = alloc.treeUsage <= alloc.quota

            val parentSuccess = chargeDelta(
                alloc.parentWallet,
                min(splitAmount, maxToSpend),
                leaf = false
            )

            if (!parentSuccess || !localSuccess) anyFailure = true
        } else {
            alloc.treeUsage += splitAmount
            val success = alloc.treeUsage <= alloc.quota
            if (!success) anyFailure = true
        }
    }
    return !anyFailure
}
*/
/*

fun updateUsage(
    walletId: Int,
    localUsageOrDelta: Long,
    isDelta: Boolean = false,
): Boolean {
    val wallet = wallets[walletId]
    val validAllocations = wallet.allocations
        .map { allocations[it] }
        .filter { it.isActiveNow() }

    val combinedAllocationsToChildren: Map<Int, Long> = allocations
        .filter { it.parentWallet == walletId }
        .groupBy { it.belongsTo }
        .mapValues { (_, allocs) -> allocs.sumOf { it.quota } }
//    val allocatedToMe = validAllocations.sumOf { it.quota }
    var cappedUsage = 0L

    val parentWalletToCombinedQuota = validAllocations
        .groupBy { it.parentWallet }
        .mapValues { (_, allocs) -> allocs.sumOf { it.quota } }

    val split =
        if (!isDelta) proposeSplit2(validAllocations, localUsageOrDelta)
        else proposeSplit(validAllocations, localUsageOrDelta)

    for ((alloc, usage) in validAllocations.zip(split)) {
        val delta = if (isDelta) usage else usage - alloc.localUsage
        if (!isDelta) alloc.localUsage = usage
        alloc.treeUsage += delta
        alloc.actualTreeUsage += delta

        cappedUsage -= alloc.treeUsage
        if (cappedUsage < 0) {
            alloc.treeUsage += cappedUsage
            cappedUsage = 0
        }

        if (alloc.parentWallet != null) {
            updateUsage(
                alloc.parentWallet,
                delta,
                isDelta = true,
            )
        }
    }

    return validAllocations.any { it.hasResources() }
}
 */

val wallets = arrayOf(
    Wallet(id = 0, intArrayOf(0)), // 0
    Wallet(id = 1, intArrayOf(1, 12)), // 1
    Wallet(id = 2, intArrayOf(2, 10)), // 2
    Wallet(id = 3, intArrayOf(3, 4, 5)), // 3
    Wallet(id = 4, intArrayOf(6, 7, 8, 11)), // 4
    Wallet(id = 5, intArrayOf(9)), // 5
)

val allocations = arrayOf(
    Allocation(belongsTo = 0, parentWallet = null, quota = 2500), // 0

    Allocation(belongsTo = 1, parentWallet = 0, quota = 600), // 1

    Allocation(belongsTo = 2, parentWallet = 0, quota = 10000), // 2

    Allocation(belongsTo = 3, parentWallet = 1, quota = 334), // 3
    Allocation(belongsTo = 3, parentWallet = 1, quota = 333), // 4
    Allocation(belongsTo = 3, parentWallet = 1, quota = 333), // 5

    Allocation(belongsTo = 4, parentWallet = 3, quota = 1000), // 6
    Allocation(belongsTo = 4, parentWallet = 1, quota = 1000), // 7
    Allocation(belongsTo = 4, parentWallet = 2, quota = 2000), // 8

    Allocation(belongsTo = 5, parentWallet = null, quota = 2500), // 9

    Allocation(belongsTo = 2, parentWallet = 5, quota = 2000), // 10

    Allocation(belongsTo = 4, parentWallet = 5, quota = 1000), // 11

    Allocation(belongsTo = 1, parentWallet = 0, quota = 600), // 12
)

/*
val wallets = arrayOf(
    Wallet(intArrayOf(0)), // 0
    Wallet(intArrayOf(1)), // 1
    Wallet(intArrayOf(2)), // 2
)

val allocations = arrayOf(
    Allocation(belongsTo = 0, parentWallet = null, localUsage = 0, treeUsage = 0, quota = 2500), // 0

    Allocation(belongsTo = 1, parentWallet = 0, localUsage = 0, treeUsage = 0, quota = 600), // 1

    Allocation(belongsTo = 2, parentWallet = 1, localUsage = 0, treeUsage = 0, quota = 1000), // 2
)
 */

/*
val wallets = arrayOf(
    Wallet(id = 0, intArrayOf(0)), // 0
    Wallet(id = 1, intArrayOf(1, 2)), // 1
    Wallet(id = 2, intArrayOf(3, 4, 5)), // 2
    Wallet(id = 3, intArrayOf(6, 7)), // 3
    Wallet(id = 4, intArrayOf(8)), // 4
)

val allocations = arrayOf(
    Allocation(belongsTo = 0, parentWallet = null, quota = 2500), // 0

    Allocation(belongsTo = 1, parentWallet = 0, quota = 600), // 1
    Allocation(belongsTo = 1, parentWallet = 0, quota = 600), // 2

    Allocation(belongsTo = 2, parentWallet = 1, quota = 334), // 3
    Allocation(belongsTo = 2, parentWallet = 1, quota = 333), // 4
    Allocation(belongsTo = 2, parentWallet = 1, quota = 333), // 5

    Allocation(belongsTo = 3, parentWallet = 2, quota = 1000), // 6
    Allocation(belongsTo = 3, parentWallet = 1, quota = 1000), // 7

    Allocation(belongsTo = 4, parentWallet = 0, quota = 1000), // 8
)
 */

var productCategoriesAreQuotaBased = false

fun main() {
    val s1 = chargeDelta2(4, 5000)
    allocations[6].expired = true
    retireAllocation(6, productCategoriesAreQuotaBased)
}

// Wallet 0:
// - 1: 1200

// Wallet 1:
// - 2: 1000
// - 3: 1000

// Wallet 2:
// - 3: 1000