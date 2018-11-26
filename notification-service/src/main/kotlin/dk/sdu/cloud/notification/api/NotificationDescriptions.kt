package dk.sdu.cloud.notification.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

data class ListNotificationRequest(
    val type: String? = null,
    val since: Long? = null,
    override val itemsPerPage: Int?,
    override val page: Int?
) : WithPaginationRequest {
    val pagination = normalize()
}

data class CreateNotification(val user: String, val notification: Notification)

data class MarkAsReadRequest(
    val bulkId: FindByNotificationIdBulk
)

data class DeleteNotificationRequest(
    val bulkId: FindByNotificationIdBulk
)

data class DeleteResponse(val failures: List<Long>)
data class MarkResponse(val failures: List<Long>)


object NotificationDescriptions : RESTDescriptions("notifications") {
    const val baseContext = "/api/notifications"

    val list = callDescription<ListNotificationRequest, Page<Notification>, CommonErrorMessage> {
        name = "list"
        method = HttpMethod.Get

        auth {
            access = AccessRight.READ
        }

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

    val markAsRead = callDescription<MarkAsReadRequest, MarkResponse, CommonErrorMessage> {
        name = "markAsRead"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"read"
            +boundTo(MarkAsReadRequest::bulkId)
        }
    }

    val markAllAsRead = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "markAllAsRead"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"read"
            +"all"
        }
    }

    val create = callDescription<CreateNotification, FindByNotificationId, CommonErrorMessage> {
        name = "create"
        method = HttpMethod.Put

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val delete = callDescription<DeleteNotificationRequest, DeleteResponse, CommonErrorMessage> {
        name = "delete"
        method = HttpMethod.Delete

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +boundTo(DeleteNotificationRequest::bulkId)
        }
    }
}
