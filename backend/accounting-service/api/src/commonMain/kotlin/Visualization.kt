package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class PieChart(val points: List<Point>) {
    @Serializable
    data class Point(val name: String, val value: Double)
}

@Serializable
data class LineChart(val lines: List<Line>) {
    @Serializable
    data class Line(val name: String, val points: List<Point>)

    @Serializable
    data class Point(val timestamp: Long, val value: Double)
}

interface VisualizationFlags {
    val filterStartDate: Long?
    val filterEndDate: Long?
    val filterType: ProductType?
    val filterProvider: String?
    val filterProductCategory: String?
    val filterAllocation: String?
    val filterWorkspace: String?
    val filterWorkspaceProject: Boolean?
}

@Serializable
data class VisualizationRetrieveUsageRequest(
    override val filterStartDate: Long? = null,
    override val filterEndDate: Long? = null,
    override val filterType: ProductType? = null,
    override val filterProvider: String? = null,
    override val filterProductCategory: String? = null,
    override val filterAllocation: String? = null,
    override val filterWorkspace: String? = null,
    override val filterWorkspaceProject: Boolean? = null,
) : VisualizationFlags

@Serializable
data class VisualizationRetrieveUsageResponse(val charts: List<UsageChart>)

@Serializable
data class UsageChart(
    val type: ProductType,
    val periodUsage: Long,
    val chargeType: ChargeType,
    val unit: ProductPriceUnit,
    val chart: LineChart,
)

@Serializable
data class VisualizationRetrieveBreakdownRequest(
    override val filterStartDate: Long? = null,
    override val filterEndDate: Long? = null,
    override val filterType: ProductType? = null,
    override val filterProvider: String? = null,
    override val filterProductCategory: String? = null,
    override val filterAllocation: String? = null,
    override val filterWorkspace: String? = null,
    override val filterWorkspaceProject: Boolean? = null,
) : VisualizationFlags

@Serializable
data class VisualizationRetrieveBreakdownResponse(val charts: List<BreakdownChart>)

@Serializable
data class BreakdownChart(
    val type: ProductType,
//    val periodUsage: Long,
    val chargeType: ChargeType,
    val unit: ProductPriceUnit,
    val chart: PieChart,
)

object Visualization : CallDescriptionContainer("accounting.visualization") {
    const val baseContext = "/api/accounting/visualization"

    init {
        title = "Usage Visualization"
        description = """
            Visualization gives the user the possibility to get an easy overview of their usage during a set period or 
            for a given product category. 
            
            There are currently two variations of usage visualization:
            
            1. Usage Chart: The usage chart shows the usage over time for each product category.
            
            Usage shown fully for product type COMPUTE for the period of week. 
            ![](/backend/accounting-service/wiki/UsageChartFull.png)
            Usage specifics for each category in the product type.
            ![](/backend/accounting-service/wiki/UsageChartInfo.png)

            2. Breakdown Chart: The breakdown chart shows the usage for the entire period divided into the
            different products used.
            Breakdown of different products usage in product type COMPUTE
            ![](/backend/accounting-service/wiki/BreakdownChart.png)
            
            ${ApiConventions.nonConformingApiWarning}

        """.trimIndent()
    }

    val retrieveUsage = call<VisualizationRetrieveUsageRequest, VisualizationRetrieveUsageResponse,
            CommonErrorMessage>("retrieveUsage") {
        httpRetrieve(baseContext, "usage")
    }

    val retrieveBreakdown = call<VisualizationRetrieveBreakdownRequest, VisualizationRetrieveBreakdownResponse,
            CommonErrorMessage>("retrieveBreakdown") {
        httpRetrieve(baseContext, "breakdown")
    }
}
