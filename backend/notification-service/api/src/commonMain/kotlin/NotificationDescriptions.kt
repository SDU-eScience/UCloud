package dk.sdu.cloud.notification.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.FakeOutgoingCall
import dk.sdu.cloud.calls.client.IngoingCallResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class ListNotificationRequest(
    val type: String? = null,
    val since: Long? = null,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null,
) : WithPaginationRequest

@Serializable
data class CreateNotification(val user: String, val notification: Notification)

@Serializable
data class FindByNotificationIdBulk(val ids: String)

val FindByNotificationIdBulk.normalizedIds: List<Long>
    get() = ids.split(",").map { it.toLongOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest) }

fun FindByNotificationIdBulk(ids: List<Long>) = FindByNotificationIdBulk(ids.joinToString(","))

typealias MarkAsReadRequest = FindByNotificationIdBulk

typealias DeleteNotificationRequest = FindByNotificationIdBulk

@Serializable
data class DeleteResponse(val failures: List<Long>)

@Serializable
data class MarkResponse(val failures: List<Long>)

typealias SubscriptionRequest = Unit
typealias SubscriptionResponse = Notification

@Serializable
data class InternalNotificationRequest(val user: String, val notification: Notification)
typealias InternalNotificationResponse = Unit

@TSTopLevel
object NotificationDescriptions : CallDescriptionContainer("notifications") {
    const val baseContext = "/api/notifications"

    init {
        description = """Notifications help users stay up-to-date with events in UCloud.
            
Powers the notification feature of UCloud. Other services can call this
service to create a new notification for users. Notifications are
automatically delivered to any connected frontend via websockets.

![](/backend/notification-service/wiki/NotificationFlow.png)
        """.trimIndent()
    }

    override fun documentation() {
        useCase("create", "Creating a notification") {
            val ucloud = ucloudCore()
            success(
                create,
                CreateNotification(
                    "User#1234",
                    Notification(
                        "MY_NOTIFICATION_TYPE",
                        "Something has happened",
                        meta = JsonObject(
                            mapOf(
                                "myParameter" to JsonPrimitive(42)
                            )
                        )
                    )
                ),
                FindByNotificationId(56123),
                ucloud
            )
        }

        useCase("subscription", "Listening to notifications") {
            val user = basicUser()
            subscription(
                subscription,
                SubscriptionRequest,
                user,
                protocol = {
                    add(
                        UseCaseNode.RequestOrResponse.Response(
                            IngoingCallResponse.Ok(
                                Notification(
                                    "MY_NOTIFICATION_TYPE",
                                    "Something has happened",
                                    meta = JsonObject(
                                        mapOf(
                                            "myParameter" to JsonPrimitive(42)
                                        )
                                    ),
                                    id = 56123,
                                    read = false,
                                ),
                                HttpStatusCode.OK,
                                FakeOutgoingCall
                            )
                        )
                    )

                }
            )

            success(
                markAsRead,
                MarkAsReadRequest("56123"),
                MarkResponse(emptyList()),
                user
            )
        }

        useCase("list-and-clear", "List and Clear notifications") {
            val user = basicUser()
            success(
                list,
                ListNotificationRequest(),
                Page(
                    1,
                    50,
                    0,
                    listOf(
                        Notification(
                            "MY_NOTIFICATION_TYPE",
                            "Something has happened",
                            meta = JsonObject(
                                mapOf(
                                    "myParameter" to JsonPrimitive(42)
                                )
                            ),
                            id = 56123,
                            read = false,
                        )
                    )
                ),
                user
            )

            success(markAllAsRead, Unit, Unit, user)
        }
    }

    val list = call<ListNotificationRequest, Page<Notification>, CommonErrorMessage>("list") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(ListNotificationRequest::type)
                +boundTo(ListNotificationRequest::since)
                +boundTo(ListNotificationRequest::itemsPerPage)
                +boundTo(ListNotificationRequest::page)
            }
        }
    }

    val markAsRead = call<MarkAsReadRequest, MarkResponse, CommonErrorMessage>("markAsRead") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"read"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val markAllAsRead = call<Unit, Unit, CommonErrorMessage>("markAllAsRead") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post
            path {
                using(baseContext)
                +"read"
                +"all"
            }
        }
    }

    val create = call<CreateNotification, FindByNotificationId, CommonErrorMessage>("create") {
        auth {
            roles = Roles.PRIVILEGED
            access = AccessRight.READ_WRITE
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

    val delete = call<DeleteNotificationRequest, DeleteResponse, CommonErrorMessage>("delete") {
        auth {
            roles = Roles.PRIVILEGED
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

    val subscription = call<SubscriptionRequest, SubscriptionResponse, CommonErrorMessage>("subscription") {
        audit<SubscriptionRequest> {
            longRunningResponseTime = true
        }

        auth {
            access = AccessRight.READ
        }

        websocket(baseContext)
    }

    val internalNotification =
        call<InternalNotificationRequest, InternalNotificationResponse, CommonErrorMessage>("internalNotification") {
            auth {
                roles = Roles.PRIVILEGED
                access = AccessRight.READ
            }

            documentation {
                summary = "Notifies an instance of this service that it should notify an end-user"
            }

            websocket(baseContext)
        }
}
