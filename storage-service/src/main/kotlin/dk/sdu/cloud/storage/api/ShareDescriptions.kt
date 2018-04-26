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
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPagination

data class CreateShareRequest(
    val sharedWith: String,
    val path: String,
    val rights: Set<AccessRight>
)

data class SharesByPath(
    val path: String,
    val sharedBy: String,
    val sharedByMe: Boolean,
    val shares: List<MinimalShare>
)

data class MinimalShare(
    val id: ShareId,
    val sharedWith: String,
    val rights: Set<AccessRight>,
    val state: ShareState
)
fun Share.minimalize(): MinimalShare =
    MinimalShare(id ?: throw NullPointerException("id must be != null"), sharedWith, rights, state)

object ShareDescriptions : RESTDescriptions(StorageServiceDescription) {
    private const val baseContext = "/api/shares"

    val list = callDescription<ListSharesRequest, Page<SharesByPath>, CommonErrorMessage> {
        prettyName = "listShare"
        method = HttpMethod.GET

        path {
            using(baseContext)
        }

        params {
            +boundTo(ListSharesRequest::itemsPerPage)
            +boundTo(ListSharesRequest::page)
        }
    }

    val create = callDescription<CreateShareRequest, FindByShareId, CommonErrorMessage> {
        prettyName = "createShare"
        method = HttpMethod.PUT

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val update = callDescription<Share, Unit, CommonErrorMessage> {
        prettyName = "updateShare"
        method = HttpMethod.POST

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val revoke = callDescription<FindByShareId, Unit, CommonErrorMessage> {
        prettyName = "revokeShare"
        method = HttpMethod.POST

        path {
            using(baseContext)
            +"revoke"
            +boundTo(FindByShareId::id)
        }
    }

    val reject = callDescription<FindByShareId, Unit, CommonErrorMessage> {
        prettyName = "rejectShare"
        method = HttpMethod.POST

        path {
            using(baseContext)
            +"reject"
            +boundTo(FindByShareId::id)
        }
    }

    val accept = callDescription<FindByShareId, Unit, CommonErrorMessage> {
        prettyName = "acceptShare"
        method = HttpMethod.POST

        path {
            using(baseContext)
            +"accept"
            +boundTo(FindByShareId::id)
        }
    }
}