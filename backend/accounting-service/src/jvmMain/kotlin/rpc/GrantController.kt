package dk.sdu.cloud.grant.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.grants.GrantComments
import dk.sdu.cloud.accounting.api.grants.GrantTemplates
import dk.sdu.cloud.accounting.api.grants.GrantsReference
import dk.sdu.cloud.accounting.api.projects.*
import dk.sdu.cloud.accounting.services.grants.GrantApplicationService
import dk.sdu.cloud.accounting.services.grants.GrantCommentService
import dk.sdu.cloud.accounting.services.grants.GrantSettingsService
import dk.sdu.cloud.accounting.services.grants.GrantTemplateService
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
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
        //GRANTS
        implement(Grants.updateApplicationState) {
            applications.updateStatus(actorAndProject, request )
            ok(Unit)
        }

        implement(Grants.closeApplication) {
            val updates = bulkRequestOf(request.items.map { UpdateApplicationState(it.applicationId, GrantApplication.State.CLOSED, false) })
            applications.updateStatus(actorAndProject, updates)
            ok(Unit)
        }

        implement(Grants.submitApplication) {
            ok(applications.submit(actorAndProject, request))
        }

        implement(Grants.editApplication) {
            applications.editApplication(actorAndProject, request)
            ok(Unit)
        }

        implement(Grants.browseApplications) {
            (applications.browseApplications(actorAndProject, request, request, request.filter))
        }

        implement(Grants.browseProducts) {
            ok(GrantsBrowseProductsResponse(applications.retrieveProducts(actorAndProject, request)))
        }

        implement(Grants.browseProjects) {
            ok(settings.browse(actorAndProject, request))
        }

        implement(Grants.browseAffiliations) {
            val app = comments.viewComments(actorAndProject, ViewApplicationRequest(request.grantId))
            ok(settings.browse(
                ActorAndProject(Actor.SystemOnBehalfOfUser(""//app.application
                    ), null),
                request
            ))
        }

        implement(Grants.transferApplication) {
            //applications.transferApplication(actorAndProject, request)
            ok(Unit)
        }

        implement(Grants.viewApplication) {
            ok(comments.viewComments(actorAndProject, request))
        }

        //COMMENTS
        implement(GrantComments.createComment) {
            //comments.postComment(actorAndProject, request)
            ok(Unit)
        }

        implement(GrantComments.deleteComment) {
            //comments.deleteComment(actorAndProject, request)
            ok(Unit)
        }

        //GRANT REFERENCE ID
        implement(GrantsReference.updateReferenceId) {
            applications.editReferenceID(actorAndProject, request)
            ok(Unit)
        }

        //PROJECT GRANT SETTINGS
        implement(GrantSettings.uploadRequestSettings) {
            //settings.uploadRequestSettings(actorAndProject, request)
            ok(Unit)
        }

        implement(GrantSettings.retrieveRequestSettings) {
            ok(settings.fetchSettings(actorAndProject, request.projectId))
        }

        //PROJECT ENABLED STATUS
        implement(ProjectEnabled.setEnabledStatus) {
           // settings.setEnabledStatus(actorAndProject, request.projectId, request.enabledStatus)
            ok(Unit)
        }

        implement(ProjectEnabled.isEnabled) {
            ok(IsEnabledResponse(settings.isEnabled(request.projectId)))
        }

        //PROJECT LOGO
        implement(ProjectLogo.retrieveLogo) {
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

        implement(ProjectLogo.uploadLogo) {
            ok(
                settings.uploadLogo(
                    actorAndProject,
                    request.projectId,
                    (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull(),
                    (ctx as HttpCall).call.request.receiveChannel()
                )
            )
        }

        //PROJECT DESCRIPTIONS
        implement(ProjectTextDescription.uploadDescription) {
            //settings.uploadDescription(actorAndProject, request.projectId, request.description)
            ok(Unit)
        }

        implement(ProjectTextDescription.retrieveDescription) {
            ok(RetrieveDescriptionResponse(settings.fetchDescription(request.projectId)))
        }

        //GRANT TEMPLATES
        implement(GrantTemplates.uploadTemplates) {
            //templates.uploadTemplates(actorAndProject, request)
            ok(Unit)
        }

        implement(GrantTemplates.retrieveTemplates) {
            ok(templates.fetchTemplates(actorAndProject, request.projectId))
        }

    }
}
