package dk.sdu.cloud.task.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class ListRequest(
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest
typealias ListResponse = Page<Task>

typealias ViewRequest = FindByStringId
typealias ViewResponse = Task

typealias ListenRequest = Unit
typealias ListenResponse = TaskUpdate

@Serializable
data class CreateRequest(val title: String, val owner: String, val initialStatus: String? = null)
typealias CreateResponse = Task

@Serializable
data class PostStatusRequest(val update: TaskUpdate)
typealias PostStatusResponse = Unit

typealias MarkAsCompleteRequest = FindByStringId
typealias MarkAsCompleteResponse = Unit

@TSTopLevel
object Tasks : CallDescriptionContainer("task") {
    private val baseContext = "/api/tasks"

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

        websocket(baseContext)
    }

    val view = call<ViewRequest, ViewResponse, CommonErrorMessage>("view") {
        auth {
            access = AccessRight.READ
        }

        http {
            path {
                using(baseContext)
                +UCloudApi.RETRIEVE
            }

            params { +boundTo(ViewRequest::id) }
        }

        websocket(baseContext)
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
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }

        websocket(baseContext)
    }

    val postStatus = call<PostStatusRequest, PostStatusResponse, CommonErrorMessage>("postStatus") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"postStatus"
            }

            body { bindEntireRequestFromBody() }
        }

        websocket(baseContext)
    }

    val markAsComplete = call<MarkAsCompleteRequest, MarkAsCompleteResponse, CommonErrorMessage>("markAsComplete") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"markAsComplete"
            }

            body { bindEntireRequestFromBody() }
        }

        websocket(baseContext)
    }
}
