package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.Actor
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.grant.api.Application
import dk.sdu.cloud.grant.api.ApplicationWithComments
import dk.sdu.cloud.grant.api.Comment
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

object CommentTable : SQLTable("grant.comments") {
    val applicationId = long("application_id", notNull = true)
    val comment = text("comment", notNull = true)
    val postedBy = text("posted_by", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val id = long("id", notNull = true)
}

class GrantCommentService(
    private val applications: GrantApplicationService,
    private val notifications: GrantNotificationService,
    private val projectCache: ProjectCache
) {
    suspend fun addComment(
        ctx: DBContext,
        actor: Actor,
        id: Long,
        comment: String
    ) {
        lateinit var application: Application
        ctx.withSession { session ->
            application = checkPermissions(session, id, actor).first
            session
                .sendPreparedStatement(
                    {
                        setParameter("postedBy", actor.safeUsername())
                        setParameter("id", id)
                        setParameter("comment", comment)
                    },
                    "insert into \"grant\".comments (application_id, comment, posted_by) values (:id, :comment, :postedBy)"
                )
        }

        val admins = projectCache.admins.get(application.resourcesOwnedBy)
        if (admins != null && (admins.find { it.username == actor.username } != null) ) {
            //admin wrote comment
            notifications.notify(
                GrantNotification(
                    application,
                    adminMessage= null,
                    userMessage =
                    UserGrantNotificationMessage(
                        subject = { "Comment on Application" },
                        type = "COMMENT_GRANT_APPLICATION",
                        email = Mail.NewCommentOnApplicationMail(
                            actor.safeUsername(),
                            application.resourcesOwnedByTitle,
                            application.grantRecipientTitle
                        ),
                        application.requestedBy
                    )
                ),
                actor.safeUsername(),
                JsonObject(mapOf("appId" to JsonPrimitive(application.id))),
            )
        }
        else {
            notifications.notify(
                GrantNotification(
                    application,
                    AdminGrantNotificationMessage(
                        subject = { "Comment on Application" },
                        type = "COMMENT_GRANT_APPLICATION",
                        Mail.NewCommentOnApplicationMail(
                            actor.safeUsername(),
                            application.resourcesOwnedByTitle,
                            application.grantRecipientTitle
                        )
                    ),
                    userMessage = null
                ),
                actor.safeUsername(),
                JsonObject(mapOf("appId" to JsonPrimitive(application.id))),
            )
        }
    }

    suspend fun deleteComment(
        ctx: DBContext,
        actor: Actor,
        commentId: Long
    ) {
        ctx.withSession { session ->
            val row = session
                .sendPreparedStatement(
                    { setParameter("commentId", commentId) },
                    "delete from \"grant\".comments where id = :commentId returning application_id, posted_by"
                )
                .rows.singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            val projectId = row.getLong(0)!!
            val postedBy = row.getString(1)!!

            checkPermissions(session, projectId, actor)
            if (actor !is Actor.System && actor.safeUsername() != postedBy) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }
        }
    }

    suspend fun viewComments(
        ctx: DBContext,
        actor: Actor,
        applicationId: Long
    ): ApplicationWithComments {
        return ctx.withSession { session ->
            val (application, approver) = applications.viewApplicationById(session, actor, applicationId)
            val comments = session
                .sendPreparedStatement(
                    {
                        setParameter("applicationId", applicationId)
                    },

                    """
                        select * from "grant".comments
                        where application_id = :applicationId
                        order by comments.created_at
                        limit 5000
                    """
                )
                .rows
                .map {
                    Comment(
                        it.getField(CommentTable.id),
                        it.getField(CommentTable.postedBy),
                        it.getField(CommentTable.createdAt).toTimestamp(),
                        it.getField(CommentTable.comment)
                    )
                }

            ApplicationWithComments(application, comments, approver)
        }
    }

    private suspend fun checkPermissions(
        session: AsyncDBConnection,
        id: Long,
        actor: Actor
    ): Pair<Application, Boolean> {
        return applications.viewApplicationById(session, actor, id)
    }

    private fun LocalDateTime.toTimestamp(): Long = toDateTime(DateTimeZone.UTC).millis
}
