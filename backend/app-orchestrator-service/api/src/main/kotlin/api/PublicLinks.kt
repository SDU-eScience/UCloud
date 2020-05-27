package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class CreateLinkRequest(val url: String)
typealias CreateLinkResponse = Unit

data class ListLinkRequest(override val itemsPerPage: Int?, override val page: Int?) : WithPaginationRequest
typealias ListLinkResponse = Page<PublicLink>

data class DeleteLinkRequest(val url: String)
typealias DeleteLinkResponse = Unit

data class PublicLink(val url: String, val inUseBy: String?, val inUseByUIFriendly: String?)

object PublicLinks : CallDescriptionContainer("hpc.urls") {
    const val baseContext = "/api/hpc/urls"

    val create = call<CreateLinkRequest, CreateLinkResponse, CommonErrorMessage>("create") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val list = call<ListLinkRequest, ListLinkResponse, CommonErrorMessage>("list") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(ListLinkRequest::itemsPerPage)
                +boundTo(ListLinkRequest::page)
            }
        }
    }

    val delete = call<DeleteLinkRequest, DeleteLinkResponse, CommonErrorMessage>("delete") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }
}