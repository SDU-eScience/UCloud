package dk.sdu.cloud.accounting.services.grants

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.grants.CreateCommentRequest
import dk.sdu.cloud.accounting.api.grants.DeleteCommentRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.grant.api.ApplicationWithComments
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

    suspend fun viewComments(
        actorAndProject: ActorAndProject,
        request: ViewApplicationRequest
    ): ApplicationWithComments {
        return db.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", request.id)
                    setParameter("username", actorAndProject.actor.safeUsername())
                },
                """
                    select
                        jsonb_build_object(
                            'application', 
                            "grant".application_to_json(
                                app,
                                array_remove(array_agg(distinct ("grant".resource_request_to_json(request, pc))), null),
                                owner_project,
                                existing_project,
                                existing_project_pi.username
                            ),
                            'comments', array_remove(array_agg(distinct ("grant".comment_to_json(posted_comment))), null),
                            'approver', pm.username is not null
                        )
                    from
                        "grant".applications app join
                        "grant".requested_resources request on app.id = request.application_id join
                        accounting.product_categories pc on request.product_category = pc.id join
                        project.projects owner_project on
                            owner_project.id = app.resources_owned_by left join
                        project.projects existing_project on
                            app.grant_recipient_type = 'existing_project' and
                            existing_project.id = app.grant_recipient left join
                        project.project_members existing_project_pi on
                            existing_project_pi.role = 'PI' and
                            existing_project_pi.project_id = existing_project.id left join
                        project.project_members pm on
                            pm.project_id = app.resources_owned_by and
                            pm.username = :username and
                            (pm.role = 'ADMIN' or pm.role = 'PI') left join
                        "grant".comments posted_comment on
                            app.id = posted_comment.application_id
                    where
                        app.id = :id and
                        (
                            app.requested_by = :username or
                            pm.username is not null
                        )
                    group by 
                        app.id, app.*, existing_project.*, owner_project.*, existing_project_pi.username, pm.username
                """
            ).rows.singleOrNull()?.let { defaultMapper.decodeFromString(it.getString(0)!!) }
                ?: throw RPCException("Unknown application, does it exist?", HttpStatusCode.NotFound)
        }
    }
}
