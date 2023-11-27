package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.httpRetrieve
import kotlinx.serialization.Serializable

object Statistics : CallDescriptionContainer("jobs.statistics") {
    val baseContext = Jobs.baseContext + "/statistics"

    val retrieveStatistics = RetrieveStatistics.call

    object RetrieveStatistics {
        @Serializable
        data class Request(
            val start: Long,
            val end: Long,
        )

        val call = call("retrieveStatistics", Request.serializer(), JobStatistics.serializer(), CommonErrorMessage.serializer()) {
            httpRetrieve(baseContext)
        }
    }
}
