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
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest

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

data class UpdateShareRequest(
    val id: ShareId,
    val rights: Set<AccessRight>
)

fun Share.minimalize(): MinimalShare =
    MinimalShare(
        id ?: throw NullPointerException("id must be != null"),
        sharedWith,
        rights,
        state
    )

object ShareDescriptions : RESTDescriptions("shares") {
    const val baseContext = "/api/shares"

    val list = callDescription<ListSharesRequest, Page<SharesByPath>, CommonErrorMessage> {
        name = "listShare"
        method = HttpMethod.Get

        auth {
            access = AuthAccessRight.READ
        }

        path {
            using(baseContext)
        }

        params {
            +boundTo(ListSharesRequest::itemsPerPage)
            +boundTo(ListSharesRequest::page)
        }
    }

    val create = callDescription<CreateShareRequest, FindByShareId, CommonErrorMessage> {
        name = "createShare"
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
        name = "updateShare"
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
        name = "revokeShare"
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

    val reject = callDescription<FindByShareId, Unit, CommonErrorMessage> {
        name = "rejectShare"
        method = HttpMethod.Post

        auth {
            access = AuthAccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"reject"
            +boundTo(FindByShareId::id)
        }
    }

    val accept = callDescription<FindByShareId, Unit, CommonErrorMessage> {
        name = "acceptShare"
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
