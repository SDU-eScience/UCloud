package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.grant.api.GrantApplication
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
    val grantRecipient: GrantApplication.Recipient
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
            val grantRecipient: GrantApplication.Recipient
        )

        val rows = db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", notification.applicationId)
                },
                """
                    with max_revision as (
                        select max(revision_number) newest, application_id
                        from "grant".revisions 
                        where application_id = :id
                        group by application_id
                    )
                    select
                        app.requested_by,
                        pm.username,
                        project.title,
                        project.id,
                        coalesce(existing_project.title, f.recipient),
                        f.recipient_type
                    from
                        "grant".applications app join
                        max_revision mr on app.id = mr.application_id join
                        "grant".requested_resources rr on app.id = rr.application_id and mr.newest = rr.revision_number join
                        "grant".forms f on app.id = f.application_id and mr.newest = f.revision_number join
                        project.projects project on
                            rr.grant_giver = project.id join
                        project.project_members pm on
                            pm.project_id = project.id and
                            (pm.role = 'ADMIN' or pm.role = 'PI') left join
                        project.projects existing_project on
                            f.recipient_type = 'existing_project' and
                            f.recipient = existing_project.id
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
                        GrantApplication.Recipient.NEW_PROJECT_TYPE -> {
                            GrantApplication.Recipient.NewProject(it.getString(4)!!)
                        }
                        GrantApplication.Recipient.PERSONAL_TYPE -> {
                            GrantApplication.Recipient.PersonalWorkspace(it.getString(4)!!)
                        }
                        GrantApplication.Recipient.EXISTING_PROJECT_TYPE -> {
                            GrantApplication.Recipient.ExistingProject(it.getString(4)!!)
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
            val admins = rows.map { it.projectMember }.toSet()
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
