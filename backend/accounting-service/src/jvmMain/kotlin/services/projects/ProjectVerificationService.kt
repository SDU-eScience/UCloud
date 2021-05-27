package dk.sdu.cloud.accounting.services.projects

import dk.sdu.cloud.accounting.services.projects.ProjectQueryService.Companion.VERIFICATION_REQUIRED_EVERY_X_DAYS
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendRequest
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.flow.collect
import org.joda.time.DateTimeConstants
import org.joda.time.LocalDateTime
import dk.sdu.cloud.service.Time
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ProjectVerificationService(
    private val db: AsyncDBSessionFactory,
    private val queries: ProjectQueryService,
    private val mailCooldown: MailCooldownDao,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun sendReminders() {
        db.withTransaction { session ->
            queries.findProjectsInNeedOfVerification(session).collect { (project, user, role, title) ->
                if (mailCooldown.hasCooldown(session, user, project)) {
                    log.debug("Cooldown: $user in $project")
                    return@collect
                }

                log.info("Sending reminder to $user for $project ($role)")

                val notificationStatus = NotificationDescriptions.create.call(
                    CreateNotification(
                        user,
                        Notification(
                            "REVIEW_PROJECT",
                            "Time to review your project ($title)",
                            meta = JsonObject(mapOf(
                                "project" to JsonPrimitive(project)
                            ))
                        )
                    ),
                    serviceClient
                )

                if (notificationStatus is IngoingCallResponse.Error) {
                    log.info("Creating a notification failed! " +
                            "${notificationStatus.statusCode} ${notificationStatus.error}")
                }

                val mailStatus = MailDescriptions.send.call(
                    SendRequest(
                        user,
                        Mail.VerificationReminderMail(
                            project,
                            role.name
                        )
                    ),
                    serviceClient
                )

                if (mailStatus is IngoingCallResponse.Error) {
                    log.info("Sending a mail failed! ${mailStatus.statusCode} ${mailStatus.error}")
                }

                if (mailStatus is IngoingCallResponse.Ok || notificationStatus is IngoingCallResponse.Ok) {
                    mailCooldown.writeCooldown(session, user, project)
                } else {
                    log.warn("Unable to send both email and notification for user: $user")
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

class MailCooldownDao {
    suspend fun hasCooldown(session: AsyncDBConnection, username: String, project: String): Boolean {
        val lastEntry = session
            .sendPreparedStatement(
                {
                    setParameter("username", username)
                    setParameter("project", project)
                },
                """
                    select max(timestamp) as timestamp
                    from project.cooldowns
                    where
                        username = :username and
                        project = :project
                """
            )
            .rows
            .singleOrNull()
            ?.getField(CooldownTable.timestamp)
            ?: return false

        return (Time.now() - lastEntry.toTimestamp()) <=
                VERIFICATION_REQUIRED_EVERY_X_DAYS * DateTimeConstants.MILLIS_PER_DAY
    }

    suspend fun writeCooldown(session: AsyncDBConnection, username: String, project: String) {
        session.insert(CooldownTable)  {
            set(CooldownTable.project, project)
            set(CooldownTable.username, username)
            set(CooldownTable.timestamp, LocalDateTime(Time.now()))
        }
    }

    private object CooldownTable : SQLTable("project.cooldowns") {
        val username = text("username")
        val project = text("project")
        val timestamp = timestamp("timestamp")
    }
}
