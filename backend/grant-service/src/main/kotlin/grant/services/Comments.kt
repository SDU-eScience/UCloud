package dk.sdu.cloud.grant.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.grant.api.Application
import dk.sdu.cloud.grant.api.ApplicationWithComments
import dk.sdu.cloud.grant.api.Comment
import dk.sdu.cloud.grant.utils.newCommentTemplate
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.safeUsername
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

object CommentTable : SQLTable("comments") {
    val applicationId = long("application_id", notNull = true)
    val comment = text("comment", notNull = true)
    val postedBy = text("posted_by", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val id = long("id", notNull = true)
}

class CommentService(
    private val applications: ApplicationService,
    private val notifications: NotificationService
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
                    "insert into comments (application_id, comment, posted_by) values (:id, :comment, :postedBy)"
                )
        }

        notifications.notify(
            GrantNotification(
                application,
                GrantNotificationMessage(
                    subject = "Comment on Application",
                    type = "COMMENT_GRANT_APPLICATION",
                    message = { user, projectTitle ->
                        newCommentTemplate(user, actor.safeUsername(), projectTitle)
                    }
                )
            ),
            actor.safeUsername(),
            mapOf("appId" to application.id)
        )
    }

    suspend fun deleteComment(
        ctx: DBContext,
        actor: Actor,
        commentId: Long
    ) {
        ctx.withSession { session ->
            val projectId = session
                .sendPreparedStatement(
                    { setParameter("commentId", commentId) },
                    "delete from comments where id = :commentId returning application_id"
                )
                .rows.singleOrNull()?.getLong(0) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            checkPermissions(session, projectId, actor)
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
                        select * from comments
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
