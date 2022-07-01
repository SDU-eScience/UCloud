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

    suspend fun postComment(
        actorAndProject: ActorAndProject,
        request: BulkRequest<CreateCommentRequest>
    ): List<FindByLongId> {
        return db.withSession(remapExceptions = true) { session ->
            request.items.map { req ->
                val id = session.sendPreparedStatement(
                    {
                        setParameter("id", req.grantId)
                        setParameter("comment", req.comment)
                        setParameter("username", actorAndProject.actor.safeUsername())
                    },
                    """
                        with max_revisions as (
                            select max(revision_number) newest
                            from "grant".revisions
                            where application_id = :id
                        )
                        insert into "grant".comments
                            (application_id, comment, posted_by) 
                        select app.id, :comment, :username
                        from 
                            "grant".applications app join
                            "grant".revisions r on 
                                app.id = r.application_id join 
                            max_revisions mr on 
                                r.revision_number = mr.newest join 
                            "grant".requested_resources rr on
                                r.application_id = rr.application_id and
                                r.revision_number = rr.revision_number  left join 
                            project.project_members pm on 
                                pm.project_id = rr.grant_giver and 
                                pm.username = :username and 
                                (pm.role = 'PI' or pm.role = 'ADMIN')
                        where app.id = :id and 
                            (
                                :username = app.requested_by or 
                                pm.username is not null
                            )
                        group by app.id, :comment, :username
                        returning id
                    """
                ).rows
                    .singleOrNull()
                    ?.getLong(0) ?: throw RPCException("Unable to post your comment", HttpStatusCode.BadRequest)

                FindByLongId(id)
                // TODO Notify
            }
        }
    }

    suspend fun deleteComment(
        actorAndProject: ActorAndProject,
        request: BulkRequest<DeleteCommentRequest>
    ) {
        db.withSession(remapExceptions = true) { session ->
            request.items.forEach { req ->
                val success = session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("comment_id", req.commentId)
                        setParameter("app_id", req.grantId)
                    },
                    """
                            with max_revisions as (
                                select max(revision_number) newest
                                from "grant".revisions
                                where application_id = :app_id
                            )
                            delete from "grant".comments comment
                            using
                                "grant".applications app join
                                "grant".revisions r on 
                                    app.id = r.application_id join
                                max_revisions mr on 
                                    r.revision_number = mr.newest join 
                                "grant".requested_resources rr on 
                                    r.application_id = rr.application_id and 
                                    r.revision_number = rr.revision_number left join 
                                project.project_members pm on 
                                    pm.project_id = rr.grant_giver and 
                                    pm.username = :username and 
                                    (pm.role = 'PI' or pm.role = 'ADMIN')
                            where 
                                comment.id = :comment_id and 
                                comment.application_id = :app_id and 
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
}
