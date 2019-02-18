package dk.sdu.cloud.share.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
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

data class AcceptShareRequest(
    val id: Long,
    val createLink: Boolean?
)

object ShareDescriptions : CallDescriptionContainer("shares") {
    const val baseContext = "/api/shares"

    val list = call<ListSharesRequest, Page<SharesByPath>, CommonErrorMessage>("list") {
        auth {
            access = AuthAccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(ListSharesRequest::state)
                +boundTo(ListSharesRequest::itemsPerPage)
                +boundTo(ListSharesRequest::page)
            }
        }
    }

    val findByPath = call<FindByPathRequest, SharesByPath, CommonErrorMessage>("findByPath") {
        auth {
            access = AuthAccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"byPath"
            }

            params {
                +boundTo(FindByPathRequest::path)
            }
        }
    }

    val create = call<CreateShareRequest, FindByShareId, CommonErrorMessage>("create") {
        auth {
            access = AuthAccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body {
                bindEntireRequestFromBody()
            }
        }
    }

    val update = call<UpdateShareRequest, Unit, CommonErrorMessage>("update") {
        auth {
            access = AuthAccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body {
                bindEntireRequestFromBody()
            }
        }
    }

    val revoke = call<FindByShareId, Unit, CommonErrorMessage>("revoke") {
        auth {
            access = AuthAccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"revoke"
                +boundTo(FindByShareId::id)
            }
        }
    }

    val accept = call<AcceptShareRequest, Unit, CommonErrorMessage>("accept") {
        auth {
            access = AuthAccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"accept"
                +boundTo(AcceptShareRequest::id)
            }

            params { +boundTo(AcceptShareRequest::createLink) }
        }
    }
}
