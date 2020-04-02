package dk.sdu.cloud.project.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendRequest
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.DistributedStateFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.create
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.flow.collect
import org.joda.time.DateTimeConstants
import org.joda.time.LocalDateTime
import org.joda.time.Period
import java.net.URLEncoder

class VerificationReminder(
    private val db: AsyncDBSessionFactory,
    private val projects: ProjectDao,
    private val mailCooldown: MailCooldownDao,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun sendReminders() {
        db.withTransaction { session ->
            projects.findProjectsInNeedOfVerification(session).collect { (project, user, role) ->
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
                            "Time to review your project ($project)",
                            meta = mapOf(
                                "project" to project
                            )
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
                        "[UCloud] Time to review your project ($project)",
                        //language=html
                        """
                            <p>Hello ${user},</p> 
                            
                            <p>
                                It is time for a review of your project $project in which you are 
                                ${if (role == ProjectRole.ADMIN) " an admin" else " a PI"}.
                            </p>
                            
                            <ul>
                                <li>PIs and admins are asked to occasionally review members of their project</li>
                                <li>We ask you to ensure that only the people who need access have access</li>
                                <li>
                                    If you find someone who should not have access then remove them by clicking 'Remove'
                                    next to their name
                                </li>
                                <li>
                                    You can begin the review by clicking 
                                    <a href="https://cloud.sdu.dk/app/projects/view/${URLEncoder.encode(project, "utf-8")}">
                                        here 
                                    </a>.
                                </li>
                            </ul>
 
                        """.trimMargin()
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

fun main() {
    println(URLEncoder.encode("Test#9224", "utf-8"))
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
                    from cooldowns
                    where
                        username = ?username and
                        project = ?project
                """
            )
            .rows
            .singleOrNull()
            ?.getField(CooldownTable.timestamp)
            ?: return false

        return (System.currentTimeMillis() - lastEntry.toTimestamp()) <=
                ProjectDao.VERIFICATION_REQUIRED_EVERY_X_DAYS * DateTimeConstants.MILLIS_PER_DAY
    }

    suspend fun writeCooldown(session: AsyncDBConnection, username: String, project: String) {
        session.insert(CooldownTable)  {
            set(CooldownTable.project, project)
            set(CooldownTable.username, username)
            set(CooldownTable.timestamp, LocalDateTime.now())
        }
    }

    private object CooldownTable : SQLTable("cooldowns") {
        val username = text("username")
        val project = text("project")
        val timestamp = timestamp("timestamp")
    }
}
