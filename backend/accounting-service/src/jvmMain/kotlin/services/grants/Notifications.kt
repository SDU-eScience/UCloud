package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.grant.api.Application
import dk.sdu.cloud.mail.api.*
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import kotlinx.serialization.json.JsonObject

data class AdminGrantNotificationMessage(
    val subject: (projectTitle: String) -> String,
    val type: String,
    val email: Mail
)

data class UserGrantNotificationMessage(
    val subject: (projectTitle: String) -> String,
    val type: String,
    val email: Mail,
    val username: String
)

data class GrantNotification(
    val application: Application,
    val adminMessage: AdminGrantNotificationMessage?,
    val userMessage: UserGrantNotificationMessage?
)

class GrantNotificationService(
    private val projects: ProjectCache,
    private val serviceClient: AuthenticatedClient
) {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun notify(notification: GrantNotification, invokedBy: String, meta: JsonObject = JsonObject(emptyMap())) {
        with(notification) {
            val title = projects.ancestors.get(application.resourcesOwnedBy)?.last()?.title ?: return

            val admins =
                if (adminMessage == null) emptyList()
                else projects.admins.get(application.resourcesOwnedBy) ?: return

            val sendRequests = ArrayList<SendRequest>()
            if (adminMessage != null) {
                for (admin in admins) {
                    if (admin.username != invokedBy) {
                        NotificationDescriptions.create.call(
                            CreateNotification(
                                admin.username,
                                Notification(adminMessage.type, adminMessage.subject(title), meta = meta)
                            ),
                            serviceClient
                        )

                        sendRequests.add(
                            SendRequest(
                                admin.username,
                                adminMessage.email
                            )
                        )
                    }
                }
            }

            if (application.requestedBy != invokedBy && userMessage != null) {
                sendRequests.add(
                    SendRequest(
                        userMessage.username,
                        userMessage.email
                    )
                )

                NotificationDescriptions.create.call(
                    CreateNotification(
                        application.requestedBy,
                        Notification(userMessage.type, userMessage.subject(title), meta = meta)
                    ),
                    serviceClient
                )
            }

            MailDescriptions.sendBulk.call(
                SendBulkRequest(sendRequests),
                serviceClient
            )
        }
    }
}
