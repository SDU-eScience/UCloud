package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.httpRetrieve
import kotlinx.serialization.Serializable

object VisualizationV2 : CallDescriptionContainer("accounting.v2.visualization") {
    const val baseContext = "/api/accounting/v2/visualization"

    val retrieveCharts = RetrieveCharts.call

    object RetrieveCharts {
        @Serializable
        data class Request(
            val start: Long,
            val end: Long,
        )

        /*
        @Serializable
        data class Response(
            val wallets: List<WalletV2>,
            val charts: List<ChartsForCategory>,
        )

        @Serializable
        data class ChartsForCategory(
            val categoryId: ProductCategoryIdV2,
            val usageByProject: UsageChart,
            val usageByResearchField: UsageChart,
            val usageOverTime: UsageOverTime,
        )

        @Serializable
        data class UsageChart(val dataPoints: List<DataPoint>) {
            @Serializable
            data class DataPoint(
                val title: String,
                val usage: Long,

                val projectId: String? = null,
            )
        }

        @Serializable
        data class UsageOverTime(val dataPoints: List<DataPoint>) {
            @Serializable
            data class DataPoint(
                val timestamp: Long,
                val usage: Long,
            )
        }
         */

        val call = call("retrieveCharts", Request.serializer(), Charts.serializer(), CommonErrorMessage.serializer()) {
            httpRetrieve(baseContext, "charts")
        }
    }
}

// Types
// =====================================================================================================================
