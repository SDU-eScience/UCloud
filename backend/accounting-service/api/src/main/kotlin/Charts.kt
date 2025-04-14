package dk.sdu.cloud.accounting.api

import kotlinx.serialization.Serializable

@Serializable
data class UsageOverTimeDatePointAPI(
    val usage: Double,
    val quota: Long,
    val timestamp: Long
)

@Serializable
data class UsageOverTimeAPI(
    var data: List<UsageOverTimeDatePointAPI>
)

@Serializable
data class BreakdownByProjectPointAPI(
    val title: String,
    val projectId: String?,
    val usage: Double,
)

@Serializable
data class BreakdownByProjectAPI (
    var data: List<BreakdownByProjectPointAPI>
)

@Serializable
data class ChartsAPI (
    val categories: List<ProductCategory>,
    val allocGroups: List<AllocationGroupWithProductCategoryIndex>,
    val charts: List<ChartsForCategoryAPI>
)

@Serializable
data class ChartsForCategoryAPI (
    val categoryIndex: Int,
    val overTime: UsageOverTimeAPI,
    val breakdownByProject: BreakdownByProjectAPI
)

@Serializable
data class AllocationGroupWithProductCategoryIndex(
    val group: AllocationGroup,
    val productCategoryIndex: Int
)

@Serializable
data class ProjectTreeNode(
    val walletId: Int,
    val projectTitle: String,
    val localUsage: Long,
    val retiredLocalUsage: Long,
    val treeUsage: Long,
    val children: MutableSet<Int>
)