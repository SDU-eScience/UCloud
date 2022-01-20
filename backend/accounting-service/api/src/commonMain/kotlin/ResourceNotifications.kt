package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.documentation
import dk.sdu.cloud.calls.httpRetrieve
import dk.sdu.cloud.calls.httpUpdate
import kotlinx.serialization.Serializable

@Serializable
data class ResourceNotification(
    val id: Long,
    val username: String,
    val resource: Long,
)


typealias ResourceNotificationsRetrieveResponse = BulkResponse<ResourceNotification>

object ResourceNotifications: CallDescriptionContainer("accounting.resourcenotifications") {
    const val baseContext = "/api/accounting/resourceNotifications"

    val retrieve = call<Unit, ResourceNotificationsRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.PROVIDER)

        documentation {
            summary = "Pulls the database for more `ResourceNotification`s"
            description = """
                    This request fetches a new batch of `ResourceNotification`s. The provider should aim to handle all
                    notifications as soon as possible. Once a notification has been handled, the provider should call
                    `ResourceNotifications.markAsRead` with the appropriate `id`s. A good provider implementation should
                    be able to handle receiving the same notification twice.
                    
                    It is recommended that a provider calls this endpoint immediately after starting.
                """.trimIndent()
        }
    }

    val markAsRead = call<BulkRequest<FindByStringId>, Unit, CommonErrorMessage>("markAsRead") {
        httpUpdate(baseContext, "markAsRead", roles = Roles.PROVIDER)

        documentation {
            summary = "Marks one or more `ResourceNotification` as read"
        }
    }
}

open class ResourceNotificationsProvider(
    provider: String
) : CallDescriptionContainer("accounting.resourcenotifications.provider.$provider") {
    val baseContext = "/ucloud/$provider/resourceNotifications"

    val pullRequest = call<Unit, Unit, CommonErrorMessage>("pullRequest") {
        httpUpdate(baseContext, "pullRequest")

        documentation {
            summary = "Request from UCloud that the provider pulls for more notifications"
            description = """
                    The provider is supposed to call `ResourceNotifications.retrieve` as soon as possible after receiving
                    this call. A 200 OK response can be sent immediately to this request, without dealing with any
                    notifications.
                """.trimIndent()
        }
    }
}
