package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class ListAccountingEventsRequest(
    val since: Long? = null,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest

typealias ListAccountingEventsResponse<Event> = Page<Event>

data class AccountingChartRequest(
    val since: Long? = null
)

typealias AccountingChartResponse = Chart<ChartDataPoint<Long, Long>>

interface ChartDataPoint<XType, YType> {
    val x: XType
    val y: YType
    val label: String?
}

data class Chart<DataPointType : ChartDataPoint<*, *>>(
    val xAxisLabel: String,
    val yAxisLabel: String,
    val data: List<DataPointType>
)

internal const val ACCOUNTING_NAMESPACE = "accounting"

/**
 * A collection of [RESTDescriptions] for a sub-implementation of the accounting-service. It provides an overview
 * interface for a single namespace. It is expected that all accounting-X-services implement this interface.
 *
 * @param namespace The namespace of this sub-service. It should _not_ include "accounting". Valid example: "compute"
 */
abstract class AbstractAccountingDescriptions(
    namespace: String
) : RESTDescriptions("$ACCOUNTING_NAMESPACE.$namespace") {
    val baseContext = "/api/$ACCOUNTING_NAMESPACE/$namespace"

    /**
     * Instruction from the primary account-service that this service should build a report.
     *
     * If it is not possible to build a report for a user the entire request should fail hard, as opposed to
     * delivering results with missing values. This data will be used for accounting and should be as precise as
     * possible. We don't want to send out bad reports.
     *
     * This request signals the end of a period for a user, __for this specific sub-resource__.
     *
     * Implementations should block any attempts not made by "_accounting".
     */
    val buildReport = callDescription<Unit, Unit, Unit> {
        name = "buildReport"
        method = HttpMethod.Post

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"buildReport"
        }
    }

    /**
     * Informs the caller about a list of sub-resources that this [namespace] contains.
     */
    val listResources = callDescription<Unit, Unit, Unit> {
        name = "listResources"
        method = HttpMethod.Get

        auth {
            roles = Roles.AUTHENTICATED // Note: This is a public endpoint!
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"list"
        }
    }
}

/**
 * [RESTDescriptions] for a single sub-resource.
 *
 * It is expected that services also implement [AbstractAccountingDescriptions] for the same namespace.
 *
 * @param namespace The namespace of this resource. Should not include "accounting". Valid example: "compute".
 * @param resourceType The resource type that this interface implements. Valid example: "timeUsed".
 */
abstract class AbstractAccountingResourceDescriptions<E>(
    namespace: String,
    resourceType: String
) : RESTDescriptions("$ACCOUNTING_NAMESPACE.$namespace.$resourceType") {
    val baseContext: String = "/api/$ACCOUNTING_NAMESPACE/$namespace/$resourceType"

    /**
     * Returns a concrete list of "raw" events that have contributed to the usage, for example as reported by [chart].
     */
    val listEvents = callDescription<ListAccountingEventsRequest, ListAccountingEventsResponse<E>, CommonErrorMessage> {
        name = "listEvents"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"events"
        }

        params {
            +boundTo(ListAccountingEventsRequest::since)
            +boundTo(ListAccountingEventsRequest::page)
            +boundTo(ListAccountingEventsRequest::itemsPerPage)
        }
    }

    /**
     * Returns chart data for this resource.
     */
    val chart = callDescription<AccountingChartRequest, AccountingChartResponse, CommonErrorMessage> {
        name = "chart"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"chart"
        }

        params {
            +boundTo(AccountingChartRequest::since)
        }
    }

    /**
     * Returns the current usage for this resource
     */
    val currentUsage = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "currentUsage"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"usage"
        }

        params {

        }
    }
}