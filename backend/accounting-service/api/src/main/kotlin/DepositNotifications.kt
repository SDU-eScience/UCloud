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
import kotlinx.serialization.builtins.serializer

@Serializable
data class DepositNotification(
    val id: String,
    val owner: WalletOwner,
    val category: ProductCategoryIdV2,
    val balance: Long,
)

typealias DepositNotificationsRetrieveResponse = BulkResponse<DepositNotification>

@Serializable
data class DepositNotificationsMarkAsReadRequestItem(
    val id: String,
    val providerGeneratedId: String? = null
)

object DepositNotifications : CallDescriptionContainer("accounting.depositnotifications") {
    const val baseContext = "/api/accounting/depositNotifications"

    val retrieve = call("retrieve", Unit.serializer(), BulkResponse.serializer(DepositNotification.serializer()), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, roles = Roles.PROVIDER)

        documentation {
            summary = "Pulls the database for more `DepositNotification`s"
            description = """
                    This request fetches a new batch of `DepositNotification`s. The provider should aim to handle all
                    notifications as soon as possible. Once a notification has been handled, the provider should call
                    `DepositNotifications.markAsRead` with the appropriate `id`s. A good provider implementation should
                    be able to handle receiving the same notification twice.
                    
                    It is recommended that a provider calls this endpoint immediately after starting.
                """.trimIndent()
        }
    }

    val markAsRead = call("markAsRead", BulkRequest.serializer(DepositNotificationsMarkAsReadRequestItem.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "markAsRead", roles = Roles.PROVIDER)

        documentation {
            summary = "Marks one or more `DepositNotification` as read"
        }
    }
}

open class DepositNotificationsProvider(
    provider: String
) : CallDescriptionContainer("accounting.depositnotifications.provider.$provider") {
    val baseContext = "/ucloud/$provider/depositNotifications"

    val pullRequest = call("pullRequest", Unit.serializer(), Unit.serializer(), CommonErrorMessage.serializer()) {
        httpUpdate(baseContext, "pullRequest", roles = Roles.SERVICE)

        documentation {
            summary = "Request from UCloud that the provider pulls for more notifications"
            description = """
                    The provider is supposed to call `DepositNotifications.retrieve` as soon as possible after receiving
                    this call. A 200 OK response can be sent immediately to this request, without dealing with any
                    notifications.
                """.trimIndent()
        }
    }
}
