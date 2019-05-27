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

object Shares : CallDescriptionContainer("shares") {
    const val baseContext = "/api/shares"

    val list = call<List.Request, Page<SharesByPath>, CommonErrorMessage>("list") {
        auth {
            access = AuthAccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(List.Request::sharedByMe)
                +boundTo(List.Request::itemsPerPage)
                +boundTo(List.Request::page)
            }
        }
    }

    object List {
        data class Request(
            val sharedByMe: Boolean,
            override val itemsPerPage: Int? = null,
            override val page: Int? = null
        ) : WithPaginationRequest
    }

    val findByPath = call<FindByPath.Request, SharesByPath, CommonErrorMessage>("findByPath") {
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
                +boundTo(FindByPath.Request::path)
            }
        }
    }

    object FindByPath {
        data class Request(
            val path: String
        )
    }

    val create = call<Create.Request, Create.Response, CommonErrorMessage>("create") {
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

    object Create {
        data class Request(
            val sharedWith: String,
            val path: String,
            val rights: Set<AccessRight>
        )

        data class Response(val id: ShareId)
    }

    val update = call<Update.Request, Unit, CommonErrorMessage>("update") {
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

    object Update {
        data class Request(
            val id: ShareId,
            val rights: Set<AccessRight>
        )
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

    val accept = call<Accept.Request, Unit, CommonErrorMessage>("accept") {
        auth {
            access = AuthAccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"accept"
                +boundTo(Accept.Request::id)
            }

            params { +boundTo(Accept.Request::createLink) }
        }
    }

    object Accept {
        data class Request(
            val id: Long,
            val createLink: Boolean?
        )
    }
}
