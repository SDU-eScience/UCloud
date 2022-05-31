package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.grants.CreateCommentRequest
import dk.sdu.cloud.accounting.api.grants.DeleteCommentRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.grant.api.GrantApplication
import dk.sdu.cloud.grant.api.ViewApplicationRequest
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.decodeFromString

class GrantCommentService(
    private val db: DBContext,
) {
    suspend fun postComment(
        actorAndProject: ActorAndProject,
        request: CreateCommentRequest
    ) {
        db.withSession(remapExceptions = true) { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("id", request.requestId)
                    setParameter("comment", request.comment)
                    setParameter("username", actorAndProject.actor.safeUsername())
                },
                """
                    insert into "grant".comments
                        (application_id, comment, posted_by) 
                    select :id, :comment, :username
                    from
                        "grant".applications app left join
                        project.project_members pm on
                            pm.project_id = app.resources_owned_by and
                            pm.username = :username and
                            (pm.role = 'PI' or pm.role = 'ADMIN')
                    where
                        app.id = :id and
                        (
                            :username = app.requested_by or
                            pm.username is not null
                        )
                """
            ).rowsAffected > 0

            if (!success) {
                throw RPCException("Unable to post your comment", HttpStatusCode.BadRequest)
            }

            // TODO Notify
        }
    }

    suspend fun deleteComment(
        actorAndProject: ActorAndProject,
        request: DeleteCommentRequest
    ) {
        db.withSession(remapExceptions = true) { session ->
            val success = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("id", request.commentId)
                },
                """
                    delete from "grant".comments comment
                    using
                        "grant".applications app left join
                        project.project_members pm on
                            pm.project_id = app.resources_owned_by and
                            pm.username = :username and
                            (pm.role = 'PI' or pm.role = 'ADMIN')
                    where
                        comment.id = :id and
                        comment.application_id = app.id and
                        posted_by = :username and
                        (app.requested_by = :username or pm.username is not null)
                """
            ).rowsAffected > 0L

            if (!success) {
                throw RPCException(
                    "Unable to delete this comment. Has it already been deleted?",
                    HttpStatusCode.BadRequest
                )
            }
        }
    }
}
