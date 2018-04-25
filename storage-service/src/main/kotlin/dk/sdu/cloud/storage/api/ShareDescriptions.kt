package dk.sdu.cloud.storage.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import io.netty.handler.codec.http.HttpMethod

interface WithPagination {
    val itemsPerPage: Int?
    val page: Int?

    val pagination: NormalizedPaginationRequest get() = PaginationRequest(itemsPerPage, page).normalize()
}

data class ListSharesRequest(
    val byState: ShareState? = null,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPagination

object ShareDescriptions : RESTDescriptions(StorageServiceDescription) {
    private const val baseContext = "/api/shares"

    val list = callDescription<ListSharesRequest, Page<Share>, CommonErrorMessage> {
        prettyName = "list"
        method = HttpMethod.GET

        path {
            using(baseContext)
        }

        params {
            +boundTo(ListSharesRequest::byState)
            +boundTo(ListSharesRequest::itemsPerPage)
            +boundTo(ListSharesRequest::page)
        }
    }

    val create = callDescription<Share, FindByShareId, CommonErrorMessage> {
        prettyName = "create"
        method = HttpMethod.PUT

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val update = callDescription<Share, Unit, CommonErrorMessage> {
        prettyName = "update"
        method = HttpMethod.POST

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val revoke = callDescription<FindByShareId, Unit, CommonErrorMessage> {
        prettyName = "revoke"
        method = HttpMethod.POST

        path {
            using(baseContext)
            +"revoke"
            +boundTo(FindByShareId::id)
        }
    }

    val reject = callDescription<FindByShareId, Unit, CommonErrorMessage> {
        prettyName = "reject"
        method = HttpMethod.POST

        path {
            using(baseContext)
            +"reject"
            +boundTo(FindByShareId::id)
        }
    }

    val accept = callDescription<FindByShareId, Unit, CommonErrorMessage> {
        prettyName = "accept"
        method = HttpMethod.POST

        path {
            using(baseContext)
            +"accept"
            +boundTo(FindByShareId::id)
        }
    }
}