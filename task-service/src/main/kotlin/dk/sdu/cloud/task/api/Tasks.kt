package dk.sdu.cloud.task.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.audit
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.websocket
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class ListRequest(override val itemsPerPage: Int?, override val page: Int?) : WithPaginationRequest
typealias ListResponse = Page<Task>

typealias ViewRequest = FindByStringId
typealias ViewResponse = Task

typealias ListenRequest = FindByStringId
typealias ListenResponse = TaskUpdate

data class CreateRequest(val title: String, val owner: String, val initialStatus: String? = null)
typealias CreateResponse = Task

data class PostStatusRequest(val id: String, val update: TaskUpdate)
typealias PostStatusResponse = Unit

typealias MarkAsCompleteRequest = FindByStringId
typealias MarkAsCompleteResponse = Unit

object Tasks : CallDescriptionContainer("task") {
    val baseContext = "/api/task"

    val list = call<ListRequest, ListResponse, CommonErrorMessage>("list") {
        auth {
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
            }

            params {
                +boundTo(ListRequest::itemsPerPage)
                +boundTo(ListRequest::page)
            }
        }
    }

    val view = call<ViewRequest, ViewResponse, CommonErrorMessage>("view") {
        auth {
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +boundTo(ViewRequest::id)
            }
        }
    }

    val listen = call<ListenRequest, ListenResponse, CommonErrorMessage>("listen") {
        audit<ListenRequest> {
            longRunningResponseTime = true
        }

        auth {
            access = AccessRight.READ
        }

        websocket(baseContext)
    }

    val create = call<CreateRequest, CreateResponse, CommonErrorMessage>("create") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val postStatus = call<PostStatusRequest, PostStatusResponse, CommonErrorMessage>("postStatus") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val markAsComplete = call<MarkAsCompleteRequest, MarkAsCompleteResponse, CommonErrorMessage>("markAsComplete") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEDGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +boundTo(MarkAsCompleteRequest::id)
            }
        }
    }
}
