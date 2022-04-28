package dk.sdu.cloud.grant.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.services.grants.GrantApplicationService
import dk.sdu.cloud.accounting.services.grants.GrantCommentService
import dk.sdu.cloud.accounting.services.grants.GrantSettingsService
import dk.sdu.cloud.accounting.services.grants.GrantTemplateService
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.ByteArrayInputStream

class GrantController(
    private val applications: GrantApplicationService,
    private val comments: GrantCommentService,
    private val settings: GrantSettingsService,
    private val templates: GrantTemplateService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Grants.approveApplication) {
            applications.updateStatus(actorAndProject, request.requestId, GrantApplication.State.APPROVED, true)
            ok(Unit)
        }

        implement(Grants.rejectApplication) {
            applications.updateStatus(actorAndProject, request.requestId, GrantApplication.State.REJECTED, request.notify)
            ok(Unit)
        }

        implement(Grants.closeApplication) {
            applications.updateStatus(actorAndProject, request.requestId, GrantApplication.State.CLOSED, false)
            ok(Unit)
        }

        implement(Grants.submitApplication) {
            ok(SubmitApplicationResponse(applications.submit(actorAndProject, request)))
        }

        implement(Grants.editApplication) {
            applications.editApplication(actorAndProject, request)
            ok(Unit)
        }

        implement(Grants.editReferenceId) {
            applications.editReferenceID(actorAndProject, request)
            ok(Unit)
        }

        implement(Grants.ingoingApplications) {
            ok(applications.browseIngoingApplications(actorAndProject, request, request.filter))
        }

        implement(Grants.outgoingApplications) {
            ok(applications.browseOutgoingApplications(actorAndProject, request, request.filter))
        }

        implement(Grants.retrieveProducts) {
            ok(GrantsRetrieveProductsResponse(applications.retrieveProducts(actorAndProject, request)))
        }

        implement(Grants.commentOnApplication) {
            comments.postComment(actorAndProject, request)
            ok(Unit)
        }

        implement(Grants.deleteComment) {
            comments.deleteComment(actorAndProject, request)
            ok(Unit)
        }

        implement(Grants.viewApplication) {
            ok(comments.viewComments(actorAndProject, request))
        }

        implement(Grants.uploadRequestSettings) {
            settings.uploadRequestSettings(actorAndProject, request)
            ok(Unit)
        }

        implement(Grants.readRequestSettings) {
            ok(settings.fetchSettings(actorAndProject, request.projectId))
        }

        implement(Grants.setEnabledStatus) {
            settings.setEnabledStatus(actorAndProject, request.projectId, request.enabledStatus)
            ok(Unit)
        }

        implement(Grants.isEnabled) {
            ok(IsEnabledResponse(settings.isEnabled(request.projectId)))
        }

        implement(Grants.browseProjects) {
            ok(settings.browse(actorAndProject, request))
        }

        implement(Grants.fetchLogo) {
            val logo = settings.fetchLogo(request.projectId)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            (ctx as HttpCall).call.respond(
                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength = logo.size.toLong()
                    override val contentType = ContentType.Image.Any
                    override fun readFrom(): ByteReadChannel = ByteArrayInputStream(logo).toByteReadChannel()
                }
            )

            okContentAlreadyDelivered()
        }

        implement(Grants.uploadLogo) {
            ok(
                settings.uploadLogo(
                    actorAndProject,
                    request.projectId,
                    (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull(),
                    (ctx as HttpCall).call.request.receiveChannel()
                )
            )
        }

        implement(Grants.uploadDescription) {
            settings.uploadDescription(actorAndProject, request.projectId, request.description)
            ok(Unit)
        }

        implement(Grants.fetchDescription) {
            ok(FetchDescriptionResponse(settings.fetchDescription(request.projectId)))
        }

        implement(Grants.uploadTemplates) {
            templates.uploadTemplates(actorAndProject, request)
            ok(Unit)
        }

        implement(Grants.readTemplates) {
            ok(templates.fetchTemplates(actorAndProject, request.projectId))
        }

        implement(Grants.retrieveAffiliations) {
            val app = comments.viewComments(actorAndProject, ViewApplicationRequest(request.grantId))
            ok(settings.browse(
                ActorAndProject(Actor.SystemOnBehalfOfUser(app.application), null),
                request
            ))
        }

        implement(Grants.transferApplication) {
            applications.transferApplication(actorAndProject, request)
            ok(Unit)
        }
    }
}
