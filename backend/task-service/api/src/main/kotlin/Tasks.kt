package dk.sdu.cloud.task.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.FakeOutgoingCall
import dk.sdu.cloud.calls.client.IngoingCallResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class BrowseRequest(
    override val itemsPerPage: Int? = null,
    override val itemsToSkip: Long?,
    override val next: String?,
    override val consistency: PaginationRequestV2Consistency?
) : WithPaginationRequestV2

typealias ListenRequest = Unit
typealias ListenResponse = BackgroundTask

@Serializable
data class CreateRequest(
    val user: String,
    val operation: String? = null,
    val progress: String? = null,
    val canPause: Boolean = false,
    val canCancel: Boolean = false,
)
typealias CreateResponse = BackgroundTask

@Serializable
data class PauseOrCancelRequest(val id: Long, val requestedState: TaskState)

@Serializable
data class PostStatusRequest(val update: BackgroundTaskUpdate)
typealias PostStatusResponse = Unit

typealias MarkAsCompleteRequest = FindByLongId
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
        val id = 123L
        val username = "User#1234"
        val title = "We are counting to 3"

        useCase("counting-task", "Counting to 3 (Produced by the service)") {
            val service = ucloudCore()
            success(
                create,
                CreateRequest(
                    user = username,
                    operation = "Counting to 3",
                    progress = "Count: 0",
                    canPause = false,
                    canCancel = false
                ),
                CreateResponse(
                    taskId = id,
                    createdAt = 0L,
                    modifiedAt = 0L,
                    createdBy = username,
                    provider = "K8",
                    status = BackgroundTask.Status(
                        TaskState.RUNNING,
                        "Counting to 3",
                        "Count: 0"
                    ), BackgroundTask.Specification(
                        false,
                        false
                    )
                ),
                service
            )
            repeat(3) {
                success(
                    postStatus,
                    PostStatusRequest(
                        BackgroundTaskUpdate(
                            taskId = id,
                            modifiedAt = it * 1000L,
                            newStatus = BackgroundTask.Status(
                                TaskState.RUNNING,
                                "Counting to 3",
                                "Count: ${it + 1}"
                            )
                        )
                    ),
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
                                        taskId = id,
                                        createdAt = 0L,
                                        modifiedAt = it * 1000L,
                                        createdBy = "username",
                                        provider = "K8",
                                        status = BackgroundTask.Status(
                                            TaskState.RUNNING,
                                            "Counting to 3",
                                            "Count: ${it + 1}"
                                        ),
                                        BackgroundTask.Specification(
                                            false,
                                            false
                                        )
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
                                    taskId = id,
                                    createdAt = 0L,
                                    modifiedAt = 4000L,
                                    createdBy = "username",
                                    provider = "K8",
                                    status = BackgroundTask.Status(
                                        TaskState.SUCCESS,
                                        "Counting to 3",
                                        "Done"
                                    ),
                                    BackgroundTask.Specification(
                                        false,
                                        false
                                    )
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

    val browse = call("browse", BrowseRequest.serializer(), PageV2.serializer(BackgroundTask.serializer()), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
        }

        httpBrowse(baseContext)
        websocket(baseContext)
    }

    val retrieve = call("retrieve", FindByLongId.serializer(), BackgroundTask.serializer(), CommonErrorMessage.serializer()) {
        auth {
            access = AccessRight.READ
        }

        httpRetrieve(baseContext)
        websocket(baseContext)
    }

    val listen = call("listen", ListenRequest.serializer(), ListenResponse.serializer(), CommonErrorMessage.serializer()) {
        audit(ListenRequest.serializer()) {
            longRunningResponseTime = true
        }

        auth {
            access = AccessRight.READ
        }

        websocket(baseContext)
    }

    val create = call("create", CreateRequest.serializer(), CreateResponse.serializer(), CommonErrorMessage.serializer()) {
        httpCreate(
            baseContext,
            roles = Roles.PROVIDER
        )

        websocket(baseContext) {
            auth {
                access = AccessRight.READ_WRITE
                roles = Roles.PROVIDER
            }
        }
    }

    val pauseOrCancel = call("pauseOrCancel", PauseOrCancelRequest.serializer(), PostStatusResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(
            baseContext,
            "pauseOrCancel",
            Roles.END_USER
        )

        websocket(baseContext)
    }

    val postStatus = call("postStatus", PostStatusRequest.serializer(), PostStatusResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(
            baseContext,
            "postStatus",
            Roles.PROVIDER
        )

        websocket(baseContext) {
            auth {
                access = AccessRight.READ_WRITE
                roles = Roles.PROVIDER
            }
        }
    }

    val markAsComplete = call("markAsComplete", MarkAsCompleteRequest.serializer(), MarkAsCompleteResponse.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(
            baseContext,
            "markAsComplete",
            roles = Roles.PROVIDER,
        )

        websocket(baseContext) {
            auth {
                access = AccessRight.READ_WRITE
                roles = Roles.PROVIDER
            }
        }
    }
}
