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
            Visualization gives the user access to an overview of their usage during a set period or for a given product category. 
            
            There are currently two variations of usage visualization:
            
            1. __Usage Chart:__ The usage chart shows the usage over time for each product category.
            
            ![](/backend/accounting-service/wiki/UsageChartFull.png)
            
            __Figure 1:__ Full usage shown for product type `COMPUTE` for the period of a week. 
            
            ![](/backend/accounting-service/wiki/UsageChartInfo.png)
            
            __Figure 2:__ Usage specifics for each category in the product type.

            2. __Breakdown Chart:__ The breakdown chart shows the usage for the entire period divided into the
               different products used.
               
            ![](/backend/accounting-service/wiki/BreakdownChart.png)
            
            __Figure 3:__ Breakdown of different products usage in product type `COMPUTE`.
            
        """.trimIndent()
    }

    val retrieveUsage = call("retrieveUsage", VisualizationRetrieveUsageRequest.serializer(), VisualizationRetrieveUsageResponse.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, "usage")

        documentation {
            summary = "Retrieve charts of usage over time."
        }
    }

    val retrieveBreakdown = call("retrieveBreakdown", VisualizationRetrieveBreakdownRequest.serializer(), VisualizationRetrieveBreakdownResponse.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, "breakdown")

        documentation {
            summary = "Retrieve breakdown charts of usage for the entire period."
        }
    }
}
