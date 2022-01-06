package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.grant.api.GrantRecipient
import dk.sdu.cloud.mail.api.*
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.json.JsonObject

data class AdminGrantNotificationMessage(
    val subject: NotificationContext.() -> String,
    val type: String,
    val mailToSend: NotificationContext.() -> Mail,
    val meta: NotificationContext.() -> JsonObject = { JsonObject(emptyMap()) },
)

data class UserGrantNotificationMessage(
    val subject: NotificationContext.() -> String,
    val type: String,
    val mailToSend: NotificationContext.() -> Mail,
    val meta: NotificationContext.() -> JsonObject = { JsonObject(emptyMap()) },
)

data class GrantNotification(
    val applicationId: Long,
    val adminMessage: AdminGrantNotificationMessage?,
    val userMessage: UserGrantNotificationMessage?
)

data class NotificationContext(
    val projectTitle: String,
    val projectId: String,
    val requestedBy: String,
    val grantRecipientTitle: String,
    val grantRecipient: GrantRecipient,
)

class GrantNotificationService(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun notify(
        invokedBy: String,
        notification: GrantNotification,
    ) {
        data class QueryRow(
            val requestedBy: String,
            val projectMember: String,
            val title: String,
            val projectId: String,
            val grantRecipientTitle: String,
            val grantRecipient: GrantRecipient
        )

        val rows = db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", notification.applicationId)
                },
                """
                    select
                        app.requested_by,
                        pm.username,
                        project.title,
                        project.id,
                        coalesce(existing_project.title, app.grant_recipient),
                        app.grant_recipient_type
                    from
                        "grant".applications app join
                        project.projects project on
                            app.resources_owned_by = project.id join
                        project.project_members pm on
                            pm.project_id = project.id and
                            (pm.role = 'ADMIN' or pm.role = 'PI') left join
                        project.projects existing_project on
                            app.grant_recipient_type = 'existing_project' and
                            app.grant_recipient = existing_project.id
                    where
                        app.id = :id
                """
            ).rows.map {
                QueryRow(
                    it.getString(0)!!,
                    it.getString(1)!!,
                    it.getString(2)!!,
                    it.getString(3)!!,
                    it.getString(4)!!,
                    when (it.getString(5)!!) {
                        GrantRecipient.NEW_PROJECT_TYPE -> {
                            GrantRecipient.NewProject(it.getString(4)!!)
                        }
                        GrantRecipient.PERSONAL_TYPE -> {
                            GrantRecipient.PersonalProject(it.getString(4)!!)
                        }
                        GrantRecipient.EXISTING_PROJECT_TYPE -> {
                            GrantRecipient.ExistingProject(it.getString(4)!!)
                        }
                        else -> error("database consistency error")
                    }
                )
            }
        }

        if (rows.isEmpty()) {
            log.warn("Received invalid notification request: ${notification} ($invokedBy)")
            return
        }

        val sendRequests = ArrayList<SendRequestItem>()

        with(notification) {
            val title = rows.first().title
            val requestedBy = rows.first().requestedBy
            val projectId = rows.first().projectId
            val grantRecipientTitle = rows.first().grantRecipientTitle
            val admins = rows.map { it.projectMember }
            val ctx = NotificationContext(title, projectId, requestedBy, grantRecipientTitle,
                rows.first().grantRecipient)

            if (adminMessage != null) {
                for (admin in admins) {
                    if (admin == invokedBy) continue
                    NotificationDescriptions.create.call(
                        CreateNotification(
                            admin,
                            Notification(
                                adminMessage.type,
                                adminMessage.subject(ctx),
                                meta = adminMessage.meta(ctx)
                            )
                        ),
                        serviceClient
                    )

                    sendRequests.add(
                        SendRequestItem(
                            admin,
                            adminMessage.mailToSend(ctx)
                        )
                    )
                }
            }

            if (requestedBy != invokedBy && userMessage != null) {
                sendRequests.add(
                    SendRequestItem(
                        requestedBy,
                        userMessage.mailToSend(ctx)
                    )
                )

                NotificationDescriptions.create.call(
                    CreateNotification(
                        requestedBy,
                        Notification(userMessage.type, userMessage.subject(ctx), meta = userMessage.meta(ctx))
                    ),
                    serviceClient
                )
            }

            if (sendRequests.isNotEmpty()) {
                MailDescriptions.sendToUser.call(
                    bulkRequestOf(sendRequests),
                    serviceClient
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
