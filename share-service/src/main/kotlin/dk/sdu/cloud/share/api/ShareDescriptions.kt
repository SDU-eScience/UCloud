package dk.sdu.cloud.share.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

private typealias AuthAccessRight = dk.sdu.cloud.AccessRight

data class ListSharesRequest(
    val state: ShareState? = null,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest

data class CreateShareRequest(
    val sharedWith: String,
    val path: String,
    val rights: Set<AccessRight>
)

data class FindByPathRequest(
    val path: String
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

data class UpdateShareRequest(
    val id: ShareId,
    val rights: Set<AccessRight>
)

object ShareDescriptions : RESTDescriptions("shares") {
    const val baseContext = "/api/shares"

    val list = callDescription<ListSharesRequest, Page<SharesByPath>, CommonErrorMessage> {
        name = "list"
        method = HttpMethod.Get

        auth {
            access = AuthAccessRight.READ
        }

        path {
            using(baseContext)
        }

        params {
            +boundTo(ListSharesRequest::state)
            +boundTo(ListSharesRequest::itemsPerPage)
            +boundTo(ListSharesRequest::page)
        }
    }

    val findByPath = callDescription<FindByPathRequest, SharesByPath, CommonErrorMessage> {
        name = "findByPath"
        method = HttpMethod.Get

        auth {
            access = AuthAccessRight.READ
        }

        path {
            using(baseContext)
            +"byPath"
        }

        params {
            +boundTo(FindByPathRequest::path)
        }
    }

    val create = callDescription<CreateShareRequest, FindByShareId, CommonErrorMessage> {
        name = "create"
        method = HttpMethod.Put

        auth {
            access = AuthAccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val update = callDescription<UpdateShareRequest, Unit, CommonErrorMessage> {
        name = "update"
        method = HttpMethod.Post

        auth {
            access = AuthAccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val revoke = callDescription<FindByShareId, Unit, CommonErrorMessage> {
        name = "revoke"
        method = HttpMethod.Post

        auth {
            access = AuthAccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"revoke"
            +boundTo(FindByShareId::id)
        }
    }

    val accept = callDescription<FindByShareId, Unit, CommonErrorMessage> {
        name = "accept"
        method = HttpMethod.Post

        auth {
            access = AuthAccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"accept"
            +boundTo(FindByShareId::id)
        }
    }
}
