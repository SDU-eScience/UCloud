package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

data class ComputeChartRequest(
    val project: String?,
    val group: String?,
    val pStart: Long = -1L,
    val pEnd: Long = -1L
) {
    init {
        if (pStart < -1L || pEnd < -1L) throw RPCException("Invalid period", HttpStatusCode.BadRequest)
        if (pEnd < pStart) throw RPCException("Invalid period", HttpStatusCode.BadRequest)

        if (project == null && group != null) {
            throw RPCException("Group supplied without a project", HttpStatusCode.BadRequest)
        }
    }
}

data class ComputeChartPoint(
    val timestamp: Long,
    val creditsUsed: Long
)

data class DailyComputeChart(val chart: Map<ProductCategoryId, List<ComputeChartPoint>>)
data class CumulativeUsageChart(val chart: Map<ProductCategoryId, List<ComputeChartPoint>>)

data class BreakdownPoint(
    val username: String,
    val creditsUsed: Long
)
data class BreakdownRequest(
    val project: String,
    val group: String?,
    val pStart: Long,
    val pEnd: Long
) {
    init {
        if (pStart < -1L || pEnd < -1L) throw RPCException("Invalid period", HttpStatusCode.BadRequest)
        if (pEnd < pStart) throw RPCException("Invalid period", HttpStatusCode.BadRequest)
    }
}

data class BreakdownResponse(
    val chart: Map<ProductCategoryId, List<BreakdownPoint>>
)


object AccountingCompute : CallDescriptionContainer("accounting.compute") {
    const val baseContext = "/api/accounting/compute"

    val dailyUsage = call<ComputeChartRequest, DailyComputeChart, CommonErrorMessage>("dailyUsage") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"daily"
            }

            params {
                +boundTo(ComputeChartRequest::project)
                +boundTo(ComputeChartRequest::group)
                +boundTo(ComputeChartRequest::pStart)
                +boundTo(ComputeChartRequest::pEnd)
            }
        }
    }

    val cumulativeUsage = call<ComputeChartRequest, CumulativeUsageChart, CommonErrorMessage>("cumulativeUsage") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"cumulative"
            }

            params {
                +boundTo(ComputeChartRequest::project)
                +boundTo(ComputeChartRequest::group)
                +boundTo(ComputeChartRequest::pStart)
                +boundTo(ComputeChartRequest::pEnd)
            }
        }
    }

    val breakdown = call<BreakdownRequest, BreakdownResponse, CommonErrorMessage>("breakdown") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"breakdown"
            }

            params {
                +boundTo(BreakdownRequest::project)
            }
        }
    }
}
