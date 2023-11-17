package accounting

enum ProductTypeB {
    STORAGE = 1,
    COMPUTE = 2,
    LICENSE = 3,
    INGRESS = 4,
    NETWORK_IP = 5,
}

record AccountingUnitB {
    name: String,
    namePlural: String,
    floatingPoint: Boolean,
    displayFrequencySuffix: Boolean,
}

enum AccountingFrequencyB {
    ONCE = 1,
    PERIODIC_MINUTE = 2,
    PERIODIC_HOUR = 3,
    PERIODIC_DAY = 4,
}

record ProductCategoryB {
    name: String,
    provider: String,
    productType: ProductTypeB,
    accountingUnit: AccountingUnitB,
    accountingFrequency: AccountingFrequencyB,
    freeToUse: Boolean,
}

record WalletAllocationB {
    id: Long,
    usage: Long,
    localUsage: Long,
    quota: Long,

    startDate: Long,
    endDate: Long,

    categoryIndex: Int,
}

record UsageOverTimeDataPoint {
    usage: Long,
    quota: Long,
    timestamp: Long,
}

record UsageOverTime {
    data: UsageOverTimeDataPoint[],
}

record Charts {
    categories: ProductCategoryB[],
    allocations: WalletAllocationB[],
    charts: ChartsForCategory[],
}

record ChartsForCategory {
    categoryIndex: Int,
    overTime: UsageOverTime,
}
