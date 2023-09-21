package dk.sdu.cloud.accounting.api

data class UsageReport(
        val sections: List<Section>
    ) {
    enum class HistoryAction {
        CHARGE,
        DEPOSIT,
        UPDATE
    }

    data class Section(
        val category: ProductCategoryIdV2,
        val allocationUnit: AccountingUnit,
        val chargePeriod: ChargePeriod,
        val history: List<HistoryEntry>
    )

    data class HistoryEntry(
        val timestamp: Long,

        //Total wallet balance
        val balance: Balance,

        //List of balances for each allocation in this category
        val balanceByAllocation: List<AllocationAndBalance>,

        //List of itemized Charge. When a charge is made the provider can supply
        //specifications for the charge. These can be seen here. Since it is not required
        //of the providers to supply this then the amount here will not always add up to the total usage.
        val details: List<ItemizedCharge>,

        //What action is this entry related to? Usage, deposit etc.
        val transactionId: String
    )

    data class AllocationHistoryEntry(
        val allocationId: String,
        val timestamp: Long,
        val balance: Balance,
        val relatedAction: HistoryAction,
        val transactionId: String,
        val change: Change
    )

    data class AllocationAndBalance(
        val allocationId: String,
        val balance: Balance
    )

    data class Balance(
        val treeUsage: Long,
        val localUsage: Long,
        val quota: Long
    )

    // Contains infomation about which changes are made due to the transaction.
    // If a Charge the local and tree will change. Positive number indicates higher usage, negative number should
    // therefore only happen at differential cases such as storage.
    // If a deposit or an update only quota will have a change. Positive number indicates a higher quota than before.
    data class Change(
        val localChange: Long,
        val treeChange: Long,
        val quotaChange: Long
    )

    data class ChargePeriod(
        val periodStart: Long,
        val periodEnd: Long
    )
}
