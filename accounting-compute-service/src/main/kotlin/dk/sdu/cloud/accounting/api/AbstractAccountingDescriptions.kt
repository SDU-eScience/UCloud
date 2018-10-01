package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
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

typealias AccountingChartResponse = Unit

abstract class AbstractAccountingDescriptions<E>(resourceType: String) : RESTDescriptions("accounting.$resourceType") {
    val baseContext: String = "/api/accounting/$resourceType"

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
}