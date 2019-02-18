package dk.sdu.cloud.notification.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.client.HttpClientConverter
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.HttpServerConverter
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

data class FindByNotificationIdBulk(val ids: List<Long>) : HttpClientConverter.OutgoingPath {
    override fun clientOutgoingPath(call: CallDescription<*, *, *>): String {
        return ids.joinToString(",")
    }

    companion object : HttpServerConverter.IngoingPath<FindByNotificationIdBulk> {
        override fun serverIngoingPath(
            description: CallDescription<*, *, *>,
            call: HttpCall,
            value: String
        ): FindByNotificationIdBulk {
            return FindByNotificationIdBulk(value.split(",").map { it.toLong() })
        }
    }
}

data class MarkAsReadRequest(
    val bulkId: FindByNotificationIdBulk
)

data class DeleteNotificationRequest(
    val bulkId: FindByNotificationIdBulk
)

data class DeleteResponse(val failures: List<Long>)
data class MarkResponse(val failures: List<Long>)


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
                +boundTo(MarkAsReadRequest::bulkId)
            }
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
            roles = Roles.PRIVILEDGED
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
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Delete

            path {
                using(baseContext)
                +boundTo(DeleteNotificationRequest::bulkId)
            }
        }
    }
}
