package dk.sdu.cloud.accounting.services.fifgjgigiugi

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Wallet(
    val allocations: IntArray,
)

class Allocation(
    val belongsTo: Int,
    val parentWallet: Int?,
    var localUsage: Long,
    var treeUsage: Long,
    var quota: Long,
    var actualTreeUsage: Long = treeUsage,
) {
    fun isActiveNow() = true

    fun hasResources(): Boolean {
        return treeUsage < quota
    }
}

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

// Proposes a split of usage amongst the allocations.
//
// The returned list must sum to the full amount and must have the same amount
// of elements.
//
// If we must overspend, then at most one allocation can overspend.
//
// If there is a way not to overspend (locally), then we must not overspend (locally).
fun proposeSplit(validAllocations: List<Allocation>, amount: Long): List<Long> {
    require(validAllocations.isNotEmpty())

    val factor = if (amount < 0) -1 else 1
    var remaining = abs(amount)
    val result = ArrayList<Long>()
    val orderedList = if (amount < 0) validAllocations.reversed() else validAllocations
    for (alloc in orderedList) {
        val capacity =
            if (amount < 0) alloc.treeUsage
            else max(0, alloc.quota - alloc.treeUsage)
        val toSubtract = min(remaining, capacity)
        result.add(toSubtract * factor)
        remaining -= toSubtract
    }

    if (remaining > 0) {
        result[0] += remaining * factor
    }

    return if (amount < 0) result.reversed() else result
}

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

fun chargeDelta2(
    walletId: Int,
    amount: Long,
    leaf: Boolean = true,
): Boolean {
    // Wallet is not monotonically increasing in usage
    if (amount == 0L) return true

    val wallet = wallets[walletId]
    val validAllocations = wallet.allocations
        .map { allocations[it] }
        .filter { it.isActiveNow() }

    if (validAllocations.isEmpty()) return false
    val split = proposeSplit(validAllocations, amount)

    var anyFailure = false
    for ((alloc, splitAmount) in validAllocations.zip(split)) {
        if (alloc.parentWallet != null) {
            val maxToSpend = max(0, alloc.quota - alloc.treeUsage)
            val minToSpend = max(-alloc.quota, splitAmount)

            if (leaf) alloc.localUsage += splitAmount
            alloc.treeUsage += splitAmount

            val localSuccess = alloc.treeUsage <= alloc.quota

            val amount1 = min(maxToSpend, max(minToSpend, splitAmount))
            val parentSuccess = chargeDelta2(
                alloc.parentWallet,
                amount1,
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

/*
val wallets = arrayOf(
    Wallet(intArrayOf(0)), // 0
    Wallet(intArrayOf(1, 12)), // 1
    Wallet(intArrayOf(2, 10)), // 2
    Wallet(intArrayOf(3, 4, 5)), // 3
    Wallet(intArrayOf(6, 7, 8, 11)), // 4
    Wallet(intArrayOf(9)), // 5
)

val allocations = arrayOf(
    Allocation(belongsTo = 0, parentWallet = null, localUsage = 0, treeUsage = 0, quota = 2500), // 0

    Allocation(belongsTo = 1, parentWallet = 0, localUsage = 0, treeUsage = 0, quota = 600), // 1

    Allocation(belongsTo = 2, parentWallet = 0, localUsage = 0, treeUsage = 0, quota = 10000), // 2

    Allocation(belongsTo = 3, parentWallet = 1, localUsage = 0, treeUsage = 0, quota = 334), // 3
    Allocation(belongsTo = 3, parentWallet = 1, localUsage = 0, treeUsage = 0, quota = 333), // 4
    Allocation(belongsTo = 3, parentWallet = 1, localUsage = 0, treeUsage = 0, quota = 333), // 5

    Allocation(belongsTo = 4, parentWallet = 3, localUsage = 0, treeUsage = 0, quota = 1000), // 6
    Allocation(belongsTo = 4, parentWallet = 1, localUsage = 0, treeUsage = 0, quota = 1000), // 7
    Allocation(belongsTo = 4, parentWallet = 2, localUsage = 0, treeUsage = 0, quota = 2000), // 8

    Allocation(belongsTo = 5, parentWallet = null, localUsage = 0, treeUsage = 0, quota = 2500), // 9

    Allocation(belongsTo = 2, parentWallet = 5, localUsage = 0, treeUsage = 0, quota = 2000), // 10

    Allocation(belongsTo = 4, parentWallet = 5, localUsage = 0, treeUsage = 0, quota = 1000), // 11

    Allocation(belongsTo = 1, parentWallet = 0, localUsage = 0, treeUsage = 0, quota = 600), // 12
)
 */

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

val wallets = arrayOf(
    Wallet(intArrayOf(0)), // 0
    Wallet(intArrayOf(1, 2)), // 1
    Wallet(intArrayOf(3, 4, 5)), // 2
    Wallet(intArrayOf(6, 7)), // 3
)

val allocations = arrayOf(
    Allocation(belongsTo = 0, parentWallet = null, localUsage = 0, treeUsage = 0, quota = 2500), // 0

    Allocation(belongsTo = 1, parentWallet = 0, localUsage = 0, treeUsage = 0, quota = 600), // 1
    Allocation(belongsTo = 1, parentWallet = 0, localUsage = 0, treeUsage = 0, quota = 600), // 2

    Allocation(belongsTo = 2, parentWallet = 1, localUsage = 0, treeUsage = 0, quota = 334), // 3
    Allocation(belongsTo = 2, parentWallet = 1, localUsage = 0, treeUsage = 0, quota = 333), // 4
    Allocation(belongsTo = 2, parentWallet = 1, localUsage = 0, treeUsage = 0, quota = 333), // 5

    Allocation(belongsTo = 3, parentWallet = 2, localUsage = 0, treeUsage = 0, quota = 1000), // 6
    Allocation(belongsTo = 3, parentWallet = 1, localUsage = 0, treeUsage = 0, quota = 1000), // 7
)

fun main() {
    chargeDelta2(3, 2000)
    chargeDelta2(3, -2000)
}

// Wallet 0:
// - 1: 1200

// Wallet 1:
// - 2: 1000
// - 3: 1000

// Wallet 2:
// - 3: 1000