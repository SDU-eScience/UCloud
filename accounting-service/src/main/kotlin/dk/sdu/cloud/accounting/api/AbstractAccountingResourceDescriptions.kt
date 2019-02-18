package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

/**
 * @see [AbstractAccountingResourceDescriptions.listEvents]
 */
data class ListEventsRequest(
    override val since: Long?,
    override val until: Long?,
    override val context: String?,

    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest, ContextQuery

/**
 * @see [AbstractAccountingResourceDescriptions.listEvents]
 */
typealias ListEventsResponse<Event> = Page<Event>

interface ContextQuery {
    /**
     * Timestamp in milliseconds. The context will include results with timestamps >= this value.
     *
     * If missing, defaults to 0.
     */
    val since: Long?

    /**
     * Timestamp in milliseconds. The context will include results with timestamps <= this value.
     *
     * If missing, defaults to Long.MAX_VALUE.
     */
    val until: Long?

    /**
     * Limits the query to a concrete context, such as a project.
     *
     * If missing defaults to all available contexts.
     */
    val context: String?
}

/**
 * @see ContextQuery
 */
data class ContextQueryImpl(
    override val since: Long? = null,
    override val until: Long? = null,
    override val context: String? = null
) : ContextQuery

/**
 * @see [AbstractAccountingResourceDescriptions.chart]
 */
typealias ChartRequest = ContextQueryImpl

/**
 * @see [AbstractAccountingResourceDescriptions.chart]
 */
data class ChartResponse(
    val chart: Chart<ChartDataPoint2D<Long, Long>>,
    val quota: Long?
)

/**
 * @see [AbstractAccountingResourceDescriptions.currentUsage]
 */
typealias UsageRequest = ContextQueryImpl

/**
 * @see [AbstractAccountingResourceDescriptions.currentUsage]
 */
data class UsageResponse(
    val usage: Long,
    val quota: Long? = null,
    val dataType: String? = null,
    val title: String? = null
)

internal const val ACCOUNTING_NAMESPACE = "accounting"

/**
 * An accounting event. Contains basic information that is required.
 *
 * Implementations are encouraged to add any other relevant data as well. This is only the absolute minimum to help
 * enforce a unified API.
 */
interface AccountingEvent {
    /**
     * A title for the event. This should contain a brief description of the type of event.
     *
     * Example: "Job Completed"
     */
    val title: String

    /**
     * A description of the event.
     *
     * If included this hints to the UI that it should display this description along with the event.
     *
     * Example: "Application 'foo@1.0.0' used 42:10:30 across 5 nodes."
     */
    val description: String? get() = null

    /**
     * Unix timestamp in milliseconds of when the event occurred
     */
    val timestamp: Long
}

data class AccountingResource(val name: String)

data class ListResourceResponse(
    val resources: List<AccountingResource>
)

/**
 * A single item on an invoice.
 */
data class BillableItem(
    val name: String,
    val units: Long,
    val unitPrice: SerializedMoney
)

/**
 * Part of an invoice, build by individual account-services
 */
data class InvoiceReport(
    val items: List<BillableItem>
)

data class BuildReportRequest(
    val user: String,
    val periodStartMs: Long,
    val periodEndMs: Long
)

typealias BuildReportResponse = InvoiceReport

/**
 * A collection of [RESTDescriptions] for a sub-implementation of the accounting-service. It provides an overview
 * interface for a single namespace. It is expected that all accounting-X-services implement this interface.
 *
 * @param namespace The namespace of this sub-service. It should _not_ include "accounting". Valid example: "compute"
 */
abstract class AbstractAccountingDescriptions(
    namespace: String
) : CallDescriptionContainer("$ACCOUNTING_NAMESPACE.$namespace") {
    val baseContext = "/api/$ACCOUNTING_NAMESPACE/$namespace"

    /**
     * Instruction from the primary account-service that this service should build a report.
     *
     * If it is not possible to build a report for a user the entire request should fail hard, as opposed to
     * delivering results with missing values. This data will be used for accounting and should be as precise as
     * possible. We don't want to send out bad reports.
     *
     * Implementations should block any attempts not made by "_accounting".
     */
    val buildReport = call<BuildReportRequest, BuildReportResponse, CommonErrorMessage>("buildReport") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"buildReport"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    /**
     * Informs the caller about a list of sub-resources that this [namespace] contains.
     */
    val listResources = call<Unit, ListResourceResponse, CommonErrorMessage>("listResources") {
        auth {
            roles = Roles.AUTHENTICATED // Note: This is a public endpoint!
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"list"
            }
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
abstract class AbstractAccountingResourceDescriptions<Event : AccountingEvent>(
    namespace: String,
    val resourceType: String
) : CallDescriptionContainer("$ACCOUNTING_NAMESPACE.$namespace.$resourceType") {
    // TODO This stuff does not work well for auditing.

    val baseContext: String = "/api/$ACCOUNTING_NAMESPACE/$namespace/$resourceType"

    /**
     * Returns a concrete list of "raw" events that have contributed to the usage, for example as reported by [chart].
     *
     * When processing events from Kafka, remember that Kafka usually only offers "at least once" delivery. The
     * processing code should be resilient to this.
     */
    val listEvents = call<ListEventsRequest, ListEventsResponse<Event>, CommonErrorMessage>("listEvents") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"events"
            }

            params {
                +boundTo(ListEventsRequest::since)
                +boundTo(ListEventsRequest::until)
                +boundTo(ListEventsRequest::context)

                +boundTo(ListEventsRequest::page)
                +boundTo(ListEventsRequest::itemsPerPage)
            }
        }
    }

    /**
     * Returns chart data for this resource.
     */
    val chart = call<ChartRequest, ChartResponse, CommonErrorMessage>("chart") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"chart"
            }

            params {
                +boundTo(ChartRequest::since)
                +boundTo(ChartRequest::until)
                +boundTo(ChartRequest::context)
            }
        }
    }

    /**
     * Returns usage for this resource for a given time period.
     */
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
                +boundTo(UsageRequest::context)
                +boundTo(UsageRequest::since)
                +boundTo(UsageRequest::until)
            }
        }
    }
}
