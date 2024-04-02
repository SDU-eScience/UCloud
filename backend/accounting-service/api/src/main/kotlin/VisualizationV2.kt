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

        val call = call("retrieveCharts", Request.serializer(), ChartsAPI.serializer(), CommonErrorMessage.serializer()) {
            httpRetrieve(baseContext, "charts")
        }
    }
}

// Types
// =====================================================================================================================
