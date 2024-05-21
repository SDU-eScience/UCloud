package dk.sdu.cloud.accounting.services.accounting

fun checkTreeUsageHierarchy() {
    for ((id, wallet) in walletsById) {
        for ((pId) in wallet.allocationsByParent) {
            if (pId == 0) continue

            val parent = walletsById.getValue(pId)
            val childUsage = parent.childrenUsage[id] ?: 0L
            val treeUsage = wallet.treeUsage(pId)

            if (childUsage != treeUsage) {
                error("Wrong usage in W${pId} for child W${id}: found $childUsage instead of $treeUsage (tree hierarchy)")
            }
        }
    }
}

fun checkWalletHierarchyTreeUsage(walletId: Int) {
    val localWallets = walletsById.getValue(walletId).generateLocalWallets()
    for (id in localWallets) {
        val w = walletsById.getValue(id)
        for ((pId) in w.allocationsByParent) {
            if (pId == 0) continue
            val p = walletsById.getValue(pId)
            var childUsage = p.childrenUsage[id] ?: 0L
            childUsage += p.childrenRetiredUsage[id] ?: 0L
            val treeUsage = w.treeUsage(pId)
            if (treeUsage != childUsage) {
                error("Wrong usage in W${pId} for child W${id}: found $childUsage instead of $treeUsage (treeUsage)")
            }
        }
    }
}

fun checkTreeUsageLimit() {
    for ((id, w) in walletsById) {
        for ((pId, ag) in w.allocationsByParent) {
            val quota = ag.totalActiveQuota()
            val treeUsage = ag.treeUsage
            if (treeUsage > quota) {
                error("Wrong active tree usage in W${id} for parent W${pId}: found $treeUsage > $quota (active quota)")
            }
        }
    }
}

fun checkWalletHierarchyTreeUsageLimit(walletId: Int) {
    val localWallets = walletsById.getValue(walletId).generateLocalWallets()
    for (id in localWallets) {
        val w = walletsById.getValue(id)
        for ((pId) in w.allocationsByParent) {
            if (pId == 0) continue
            val p = walletsById.getValue(pId)
            var childUsage = p.childrenUsage[id] ?: 0L
            childUsage += p.childrenRetiredUsage[id] ?: 0L
            val treeUsage = w.treeUsage(pId)
            if (treeUsage != childUsage) {
                error("Wrong usage in W${pId} for child W${id}: found $childUsage instead of $treeUsage (tree usage limit)")
            }
        }
    }
}

fun checkWalletHierarchyActiveAllocated(walletId: Int) {
    val localWallets = walletsById.getValue(walletId).generateLocalWallets()
    for (id in localWallets) {
        val w = walletsById.getValue(id)
        val activeAllocated = w.totalAllocated
        var activeChildrenUsage = 0L
        for ((_, usage) in w.childrenUsage) {
            activeChildrenUsage += usage
        }

        if (activeChildrenUsage > activeAllocated) {
            error("Wrong active children usage in W${id}: found ${activeChildrenUsage} > ${activeAllocated} (active allocated)")
        }
    }
}

fun checkWalletHierarchyAllocatedRetiredUsage(walletId: Int) {
    val localWallets = walletsById.getValue(walletId).generateLocalWallets()
    for (id in localWallets) {
        val w = walletsById.getValue(id)
        val retiredAllocated = w.totalRetiredAllocated
        val retiredChildrenUsage = w.childrenRetiredUsage.values.sum()
        if (retiredChildrenUsage > retiredAllocated) {
            error("Wrong retired children usage in W$id: found $retiredChildrenUsage > $retiredAllocated (retired allocated)")
        }
    }
}

fun checkWalletHierarchyExcessUsage(walletId: Int) {
    val localWallets = walletsById.getValue(walletId).generateLocalWallets()
    for (id in localWallets) {
        val w = walletsById.getValue(id)

        val overAllocation = w.totalAllocated + w.totalRetiredAllocated + w.localUsage - w.totalActiveQuota()

        if (overAllocation <= 0) continue

        val excessUsage = w.excessUsage
        if (excessUsage > overAllocation) {
            error("Wrong excess usage in W$id: found $excessUsage > $overAllocation (overallocation)")
        }
    }
}

fun checkWalletHierarchyLocalRetiredUsage(walletId: Int) {
    val localWallets = walletsById.getValue(walletId).generateLocalWallets()
    for (id in localWallets) {
        val w = walletsById.getValue(id)
        val localRetired = w.localRetiredUsage
        val agRetired = w.allocationsByParent.values.sumOf { ag ->
            ag.allocationSet
                .filter { !it.value }
                .keys
                .sumOf { aId -> allocations.getValue(aId).retiredUsage }
        }

        if (localRetired != agRetired) {
            error("Wrong retired usage in W$id: found $agRetired != $localRetired")
        }
    }
}

fun checkWalletHierarchyParentRetiredUsage(walletId: Int) {
    val localWallets = walletsById.getValue(walletId).generateLocalWallets()
    for (id in localWallets) {
        val w = walletsById.getValue(id)
        for ((pId, ag) in w.allocationsByParent) {
            if (pId == 0) continue
            val p = walletsById.getValue(pId)
            val childRetiredUsage = p.childrenRetiredUsage[id] ?: 0L
            val treeUsage = ag.retiredTreeUsage
            if (treeUsage != childRetiredUsage) {
                error("Wrong usage in W${pId} for child W${id}: found $childRetiredUsage instead of $treeUsage")
            }
        }
    }
}

fun checkWalletHierarchy(walletId: Int) {
    checkWalletHierarchyTreeUsage(walletId)
    checkWalletHierarchyTreeUsageLimit(walletId)
    checkWalletHierarchyActiveAllocated(walletId)
    checkWalletHierarchyAllocatedRetiredUsage(walletId)
    checkWalletHierarchyExcessUsage(walletId)
    checkWalletHierarchyLocalRetiredUsage(walletId)
    checkWalletHierarchyParentRetiredUsage(walletId)
}
