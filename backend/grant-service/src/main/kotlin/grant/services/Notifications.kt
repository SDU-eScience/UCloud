package dk.sdu.cloud.grant.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.grant.api.Application
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendBulkRequest
import dk.sdu.cloud.mail.api.SendRequest
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions

data class GrantNotificationMessage(
    val subject: String,
    val type: String,
    val message: (receiver: String, projectTitle: String) -> String
)

data class GrantNotification(
    val application: Application,
    val adminMessage: GrantNotificationMessage?,
    val userMessage: GrantNotificationMessage? = adminMessage
)

class NotificationService(
    private val projects: ProjectCache,
    private val serviceClient: AuthenticatedClient
) {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun notify(notification: GrantNotification, invokedBy: String, meta: Map<String, Any?> = emptyMap()) {
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
                                Notification(adminMessage.type, adminMessage.subject, meta = meta)
                            ),
                            serviceClient
                        )

                        sendRequests.add(
                            SendRequest(
                                admin.username,
                                adminMessage.subject,
                                adminMessage.message(admin.username, title)
                            )
                        )
                    }
                }
            }

            if (application.requestedBy != invokedBy && userMessage != null) {
                sendRequests.add(
                    SendRequest(
                        application.requestedBy,
                        userMessage.subject,
                        userMessage.message(application.requestedBy, title)
                    )
                )

                NotificationDescriptions.create.call(
                    CreateNotification(
                        application.requestedBy,
                        Notification(userMessage.type, userMessage.subject, meta = meta)
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
