package dk.sdu.cloud.notification.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

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

            websocket(baseContext)
        }
}
