package dk.sdu.cloud.grant.services

import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.safeUsername

object CommentTable : SQLTable("comments") {
    val applicationId = long("application_id", notNull = true)
    val comment = text("comment", notNull = true)
    val postedBy = text("posted_by", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val id = long("id", notNull = true)
}

class CommentService {
    suspend fun addComment(
        ctx: DBContext,
        actor: Actor,
        id: Long,
        comment: String
    ) {
        ctx.withSession { session ->
            // TODO Check ACL

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
            // TODO Check ACL
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
}
