package dk.sdu.cloud.notification.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import io.ktor.http.HttpMethod

data class ListNotificationRequest(
    val type: String? = null,
    val since: Long? = null,
    val itemsPerPage: Int? = null,
    val page: Int? = null
) {
    val pagination = PaginationRequest(itemsPerPage, page).normalize()
}

data class CreateNotification(val user: String, val notification: Notification)

object NotificationDescriptions : RESTDescriptions(NotificationServiceDescription) {
    private const val baseContext = "/api/notifications"

    val list = callDescription<ListNotificationRequest, Page<Notification>, CommonErrorMessage> {
        prettyName = "list"
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

    val markAsRead = callDescription<FindByNotificationId, Unit, CommonErrorMessage> {
        prettyName = "markAsRead"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"read"
            +boundTo(FindByNotificationId::id)
        }
    }

    val create = callDescription<CreateNotification, FindByNotificationId, CommonErrorMessage> {
        prettyName = "create"
        method = HttpMethod.Put

        path {
            using(baseContext)
        }

        body {
            bindEntireRequestFromBody()
        }
    }

    val delete = callDescription<FindByNotificationId, Unit, CommonErrorMessage> {
        prettyName = "delete"
        method = HttpMethod.Delete

        path {
            using(baseContext)
            +boundTo(FindByStringId::id)
        }
    }
}
