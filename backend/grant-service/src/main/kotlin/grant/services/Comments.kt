package dk.sdu.cloud.grant.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.grant.api.ApplicationWithComments
import dk.sdu.cloud.grant.api.Comment
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
    private val projects: ProjectCache,
    private val applications: ApplicationService
) {
    suspend fun addComment(
        ctx: DBContext,
        actor: Actor,
        id: Long,
        comment: String
    ) {
        ctx.withSession { session ->
            checkPermissions(session, id, actor)

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
    }

    suspend fun deleteComment(
        ctx: DBContext,
        actor: Actor,
        id: Long,
        commentId: Long
    ) {
        ctx.withSession { session ->
            checkPermissions(session, id, actor)

            session
                .sendPreparedStatement(
                    {
                        setParameter("appId", id)
                        setParameter("commentId", commentId)
                    },
                    "delete from comments where id = :commentId and application_id = :appId"
                )
        }
    }

    suspend fun viewComments(
        ctx: DBContext,
        actor: Actor,
        applicationId: Long
    ): ApplicationWithComments {
        return ctx.withSession { session ->
            val application = applications.viewApplicationById(session, actor, applicationId)
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
                        it.getField(CommentTable.createdAt).toTimestamp()
                    )
                }

            ApplicationWithComments(application, comments)
        }
    }

    private suspend fun checkPermissions(
        session: AsyncDBConnection,
        id: Long,
        actor: Actor
    ) {
        applications.viewApplicationById(session, actor, id)
    }

    private fun LocalDateTime.toTimestamp(): Long = toDateTime(DateTimeZone.UTC).millis
}
