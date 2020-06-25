package dk.sdu.cloud.grant.controller

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.grant.api.ApplicationStatus
import dk.sdu.cloud.grant.api.Grants
import dk.sdu.cloud.grant.services.ApplicationService
import dk.sdu.cloud.grant.services.CommentService
import dk.sdu.cloud.grant.services.SettingsService
import dk.sdu.cloud.grant.services.TemplateService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.toActor
import io.ktor.http.HttpStatusCode

class GrantController(
    private val applications: ApplicationService,
    private val comments: CommentService,
    private val settings: SettingsService,
    private val templates: TemplateService,
    private val db: DBContext
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Grants.approveApplication) {
            applications.updateStatus(
                db,
                ctx.securityPrincipal.toActor(),
                request.requestId,
                ApplicationStatus.APPROVED
            )
            ok(Unit)
        }

        implement(Grants.rejectApplication) {
            applications.updateStatus(
                db,
                ctx.securityPrincipal.toActor(),
                request.requestId,
                ApplicationStatus.REJECTED
            )
            ok(Unit)
        }

        implement(Grants.commentOnApplication) {
            comments.addComment(db, ctx.securityPrincipal.toActor(), request.requestId, request.comment)
            ok(Unit)
        }

        implement(Grants.deleteComment) {
            comments.deleteComment(db, ctx.securityPrincipal.toActor(), request.commentId)
            ok(Unit)
        }

        implement(Grants.submitApplication) {
            applications.submit(db, ctx.securityPrincipal.toActor(), request)
            ok(Unit)
        }

        implement(Grants.editApplication) {
            applications.updateApplication(
                db,
                ctx.securityPrincipal.toActor(),
                request.id,
                request.newDocument,
                request.newResources
            )
            ok(Unit)
        }

        implement(Grants.uploadTemplates) {
            templates.uploadTemplates(
                db,
                ctx.securityPrincipal.toActor(),
                ctx.project ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest),
                request
            )
            ok(Unit)
        }

        implement(Grants.uploadRequestSettings) {
            db.withSession { session ->
                val projectId = ctx.project ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

                settings.updateApplicationsFromList(
                    session,
                    ctx.securityPrincipal.toActor(),
                    projectId,
                    request.allowRequestsFrom
                )

                settings.updateAutomaticApprovalList(
                    session,
                    ctx.securityPrincipal.toActor(),
                    projectId,
                    request.automaticApproval
                )
            }
            ok(Unit)
        }

        implement(Grants.readTemplates) {
            ok(templates.fetchTemplates(db, ctx.securityPrincipal.toActor(), request.projectId))
        }

        implement(Grants.ingoingApplications) {
            ok(applications.listIngoingApplications(
                db,
                ctx.securityPrincipal.toActor(),
                ctx.project ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest),
                request.normalize()
            ))
        }

        implement(Grants.outgoingApplications) {
            ok(applications.listOutgoingApplications(
                db,
                ctx.securityPrincipal.toActor(),
                request.normalize()
            ))
        }

        implement(Grants.viewApplication) {
            ok(comments.viewComments(db, ctx.securityPrincipal.toActor(), request.id))
        }

        return@with
    }

}
