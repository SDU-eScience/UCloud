package dk.sdu.cloud.grant.services

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.safeUsername
import io.ktor.http.HttpStatusCode

object CommentTable : SQLTable("comments") {
    val applicationId = long("application_id", notNull = true)
    val comment = text("comment", notNull = true)
    val postedBy = text("posted_by", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val id = long("id", notNull = true)
}

class CommentService(
    private val projects: ProjectCache
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

    private suspend fun checkPermissions(
        session: AsyncDBConnection,
        id: Long,
        actor: Actor
    ) {
        val (projectId, requestedBy) = session
            .sendPreparedStatement(
                { setParameter("id", id) },
                """
                    select resources_owned_by, requested_by from "grant".applications where id = :id
                """
            )
            .rows
            .singleOrNull()
            ?.let { Pair(it.getString(0)!!, it.getString(1)!!) }
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (actor != Actor.System && actor.username != requestedBy &&
            !projects.isAdminOfProject(projectId, actor)
        ) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }
    }
}
