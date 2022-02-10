package dk.sdu.cloud.task.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.FakeOutgoingCall
import dk.sdu.cloud.calls.client.IngoingCallResponse
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
    private const val baseContext = "/api/tasks"

    init {
        description = """Tasks give services a way to communicate progress to end-users.

A task in UCloud displays the progress of any long-running process. Both services and providers use this functionality.
Each task is uniquely identified by a key. Each task belongs to a specific end-user. Services/providers communicate 
progress updates regularly. If the end-user is online when an update occurs, then the end-user is notified.

Providers use this functionality through one of the Control interfaces. They do not invoke the interface directly.
        """
    }

    override fun documentation() {
        val id = "b06f51d2-88af-487c-bb4c-4cc156cf24fd"
        val username = "User#1234"
        val title = "We are counting to 3"

        useCase("counting-task", "Counting to 3 (Produced by the service)") {
            val service = ucloudCore()
            success(
                create,
                CreateRequest(title, username),
                CreateResponse(id, username, "_ucloud", title, complete = false, startedAt = 0L, modifiedAt = 0L),
                service
            )
            repeat(3) {
                success(
                    postStatus,
                    PostStatusRequest(TaskUpdate(id, messageToAppend = "Count is now ${it + 1}")),
                    PostStatusResponse,
                    service
                )
            }
            success(
                markAsComplete,
                MarkAsCompleteRequest(id),
                PostStatusResponse,
                service
            )
        }

        useCase("counting-task-2", "Counting to 3 (Received by end-user)") {
            val user = basicUser()
            subscription(
                listen,
                ListenRequest,
                user,
                protocol = {
                    repeat(3) {
                        add(
                            UseCaseNode.RequestOrResponse.Response(
                                IngoingCallResponse.Ok(
                                    ListenResponse(
                                        id,
                                        messageToAppend = "Count is now ${it + 1}"
                                    ),
                                    HttpStatusCode.OK,
                                    FakeOutgoingCall
                                )
                            )
                        )
                    }
                    add(
                        UseCaseNode.RequestOrResponse.Response(
                            IngoingCallResponse.Ok(
                                ListenResponse(
                                    id,
                                    complete = true
                                ),
                                HttpStatusCode.OK,
                                FakeOutgoingCall
                            )
                        )
                    )
                }
            )
        }
    }

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
