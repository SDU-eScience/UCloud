package dk.sdu.cloud.accounting.api

data class UsageReport(
        val sections: List<Section>
    ) {
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
        val details: List<ItemizedCharge>
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

    data class ChargePeriod(
        val periodStart: Long,
        val periodEnd: Long
    )
}
