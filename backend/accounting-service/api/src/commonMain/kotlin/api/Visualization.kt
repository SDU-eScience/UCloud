package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

interface TimeRangeQuery {
    val bucketSize: Long
    val periodStart: Long
    val periodEnd: Long
}

fun TimeRangeQuery.validateTimeRange() {
    if (periodStart >= periodEnd) throw RPCException("periodStart is larger than periodEnd", HttpStatusCode.BadRequest)
    if (periodStart < 0L || periodEnd < 0L) throw RPCException("period must be > 0", HttpStatusCode.BadRequest)

    val delta = periodEnd - periodStart
    if (bucketSize >= delta) {
        throw RPCException("bucketSize is larger than period", HttpStatusCode.BadRequest)
    }

    if (bucketSize / delta > 250)  {
        throw RPCException("bucketSize would result in too many buckets", HttpStatusCode.BadRequest)
    }
}

@Serializable
data class UsageRequest(
    override val bucketSize: Long,
    override val periodStart: Long,
    override val periodEnd: Long
) : TimeRangeQuery {
    init {
        validateTimeRange()
    }
}

@Serializable
data class UsagePoint(
    val timestamp: Long,
    val creditsUsed: Long
)

@Serializable
data class UsageLine(
    val area: ProductArea,
    val category: String,
    val projectPath: String? = null,
    val projectId: String? = null,
    val points: List<UsagePoint>
)

@Serializable
data class UsageChart(
    val provider: String,
    val lines: List<UsageLine>
)

@Serializable
data class UsageResponse(
    val charts: List<UsageChart>
)

/**
 * Provides statistics and visualization of resources usage
 */
object Visualization : CallDescriptionContainer("accounting.visualization") {
    const val baseContext = "/api/accounting/visualization"

    val usage = call<UsageRequest, UsageResponse, CommonErrorMessage>("usage") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"usage"
            }

            params {
                +boundTo(UsageRequest::bucketSize)
                +boundTo(UsageRequest::periodEnd)
                +boundTo(UsageRequest::periodStart)
            }
        }
    }
}

