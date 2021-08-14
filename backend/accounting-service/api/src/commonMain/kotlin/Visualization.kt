package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class PieChart(val unit: String, val points: List<Point>) {
    @Serializable
    data class Point(val name: String, val value: Double)
}

@Serializable
data class LineChart(val unit: String, val lines: List<Line>) {
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
}

@Serializable
data class VisualizationRetrieveUsageRequest(
    @UCloudApiDoc("If true, the response will contain an entry for ProductType available, otherwise it will contain" +
            "one for every category available")
    val combineWalletsOfSameType: Boolean = true,
    override val filterStartDate: Long? = null,
    override val filterEndDate: Long? = null,
    override val filterType: ProductType? = null,
    override val filterProvider: String? = null,
    override val filterProductCategory: String? = null,
    override val filterAllocation: String? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null
) : VisualizationFlags, WithPaginationRequestV2

typealias VisualizationRetrieveUsageResponse = PageV2<VisualizationRetrieveUsageResponseItem>

@Serializable
data class VisualizationRetrieveUsageResponseItem(
    val type: ProductType,
    val productCategoryId: ProductCategoryId?,
    val periodUsage: Long,
    val chargeType: ChargeType,
    val unit: ProductPriceUnit,
    val chart: LineChart,
)

@Serializable
data class VisualizationRetrieveBreakdownRequest(
    @UCloudApiDoc("If true, the response will contain an entry for ProductType available, otherwise it will contain" +
            "one for every category available")
    val combineWalletsOfSameType: Boolean = true,
    override val filterStartDate: Long? = null,
    override val filterEndDate: Long? = null,
    override val filterType: ProductType? = null,
    override val filterProvider: String? = null,
    override val filterProductCategory: String? = null,
    override val filterAllocation: String? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null
) : VisualizationFlags, WithPaginationRequestV2

typealias VisualizationRetrieveBreakdownResponse = PageV2<VisualizationRetrieveBreakdownResponseItem>

@Serializable
data class VisualizationRetrieveBreakdownResponseItem(
    val type: ProductType,
    val productCategoryId: ProductCategoryId?,
    val periodUsage: Long,
    val chargeType: ChargeType,
    val unit: ProductPriceUnit,
    val chart: PieChart,
)

object Visualization : CallDescriptionContainer("accounting.visualization") {
    const val baseContext = "/api/accounting/visualization"

    val retrieveUsage = call<VisualizationRetrieveUsageRequest, VisualizationRetrieveUsageResponse,
            CommonErrorMessage>("retrieveUsage") {
        httpRetrieve(baseContext, "usage")
    }

    val retrieveBreakdown = call<VisualizationRetrieveBreakdownRequest, VisualizationRetrieveBreakdownResponse,
            CommonErrorMessage>("retrieveBreakdown") {
        httpRetrieve(baseContext, "breakdown")
    }
}
