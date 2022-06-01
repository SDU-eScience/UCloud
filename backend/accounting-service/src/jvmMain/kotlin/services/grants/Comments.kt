package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.accounting.api.grants.CreateCommentRequest
import dk.sdu.cloud.accounting.api.grants.DeleteCommentRequest
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*

class GrantCommentService(
    private val db: DBContext,
) {

    private suspend fun isAllowedToComment(session: AsyncDBConnection, grantId: Long, actor: ActorAndProject ):Boolean {
        return session.sendPreparedStatement(
            {
                setParameter("app_id", grantId)
                setParameter("username", actor.actor.safeUsername())
            },
            """
                select * 
                from "grant".applications a join 
                "grant".requested_resources rr on a.id = rr.application_id join
                        project.project_members pm on
                            pm.project_id = rr.grant_giver and
                            pm.username = :username and
                            (pm.role = 'PI' or pm.role = 'ADMIN')
                where a.id = :app_id
            """
        ).rows.size > 0
    }

    suspend fun postComment(
        actorAndProject: ActorAndProject,
        request: BulkRequest<CreateCommentRequest>
    ): List<FindByLongId> {
        return db.withSession(remapExceptions = true) { session ->
            request.items.map { req ->
                if (isAllowedToComment(session, req.grantId, actorAndProject)) {
                    val id = session.sendPreparedStatement(
                        {
                            setParameter("id", req.grantId)
                            setParameter("comment", req.comment)
                            setParameter("username", actorAndProject.actor.safeUsername())
                        },
                        """
                    insert into "grant".comments
                        (application_id, comment, posted_by) 
                    select :id, :comment, :username
                    returning id
                """
                    ).rows
                        .singleOrNull()
                        ?.getLong(0) ?: throw RPCException("Unable to post your comment", HttpStatusCode.BadRequest)

                    FindByLongId(id)
                    // TODO Notify
                } else {
                    throw RPCException("User not allowed", HttpStatusCode.Forbidden)
                }
            }
        }
    }

    suspend fun deleteComment(
        actorAndProject: ActorAndProject,
        request: BulkRequest<DeleteCommentRequest>
    ) {
        db.withSession(remapExceptions = true) { session ->
            request.items.forEach {req ->
                if (isAllowedToComment(session, req.grantId, actorAndProject)) {
                    val success = session.sendPreparedStatement(
                        {
                            setParameter("username", actorAndProject.actor.safeUsername())
                            setParameter("comment_id", req.commentId)
                            setParameter("app_id", req.grantId)
                        },
                        """
                        delete from "grant".comments comment
                        where id = :comment_id and application_id = :app_id
                    """
                    ).rowsAffected > 0L

                    if (!success) {
                        throw RPCException(
                            "Unable to delete this comment. Has it already been deleted?",
                            HttpStatusCode.BadRequest
                        )
                    }
                } else {
                    throw RPCException("User not allowed", HttpStatusCode.Forbidden)
                }
            }
        }
    }
}
