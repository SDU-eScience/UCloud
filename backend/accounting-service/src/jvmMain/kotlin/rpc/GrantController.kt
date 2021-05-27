package dk.sdu.cloud.grant.rpc

import dk.sdu.cloud.FindByLongId
import dk.sdu.cloud.PaginationRequest
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.accounting.api.RetrieveWalletsForProjectsRequest
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.accounting.services.grants.GrantApplicationService
import dk.sdu.cloud.accounting.services.grants.GrantCommentService
import dk.sdu.cloud.accounting.services.grants.GrantSettingsService
import dk.sdu.cloud.accounting.services.grants.GrantTemplateService
import dk.sdu.cloud.auth.api.GetPrincipalRequest
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.toActor
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.ByteArrayInputStream

class GrantController(
    private val applications: GrantApplicationService,
    private val comments: GrantCommentService,
    private val settings: GrantSettingsService,
    private val templates: GrantTemplateService,
    private val serviceClient: AuthenticatedClient,
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
                ApplicationStatus.REJECTED,
                request.notify
            )
            ok(Unit)
        }

        implement(Grants.closeApplication) {
            applications.updateStatus(
                db,
                ctx.securityPrincipal.toActor(),
                request.requestId,
                ApplicationStatus.CLOSED
            )
            ok(Unit)
        }

        implement(Grants.transferApplication) {
            applications.transferApplication(
                db,
                ctx.securityPrincipal.toActor(),
                ctx.project,
                request.applicationId,
                request.transferToProjectId
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
            val id = applications.submit(db, ctx.securityPrincipal.toActor(), request)
            ok(FindByLongId(id))
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

                settings.updateExclusionsFromList(
                    session,
                    ctx.securityPrincipal.toActor(),
                    projectId,
                    request.excludeRequestsFrom
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

        implement(Grants.readRequestSettings) {
            ok(settings.fetchSettings(db, ctx.securityPrincipal.toActor(), request.projectId))
        }

        implement(Grants.ingoingApplications) {
            ok(
                applications.listIngoingApplications(
                    db,
                    ctx.securityPrincipal.toActor(),
                    ctx.project ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest),
                    request.normalize(),
                    request.filter
                )
            )
        }

        implement(Grants.outgoingApplications) {
            ok(
                applications.listOutgoingApplications(
                    db,
                    ctx.securityPrincipal.toActor(),
                    request.normalize(),
                    request.filter
                )
            )
        }

        implement(Grants.viewApplication) {
            ok(comments.viewComments(db, ctx.securityPrincipal.toActor(), request.id))
        }

        implement(Grants.setEnabledStatus) {
            settings.setEnabledStatus(db, ctx.securityPrincipal.toActor(), request.projectId, request.enabledStatus)
            ok(Unit)
        }

        implement(Grants.isEnabled) {
            ok(IsEnabledResponse(settings.isEnabled(db, request.projectId)))
        }

        implement(Grants.browseProjects) {
            ok(settings.browse(db, ctx.securityPrincipal.toActor(), request.normalize()))
        }

        implement(Grants.retrieveAffiliations) {
            val application = applications.viewApplicationById(db, ctx.securityPrincipal.toActor(), request.grantId)
            val username = application.first.requestedBy
            val principal = UserDescriptions.retrievePrincipal.call(
                GetPrincipalRequest(username),
                serviceClient
            ).orThrow()
            val user = when (principal) {
                is Person -> {
                    SecurityPrincipal(
                        principal.id,
                        principal.role,
                        principal.firstNames,
                        principal.lastName,
                        principal.uid,
                        principal.email,
                        principal.twoFactorAuthentication,
                        organization = if (principal is Person.ByWAYF) principal.organizationId else null
                    )
                }
                else -> throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "user not found")
            }
            val affiliatedProjects = settings.browse(
                db,
                user.toActor(),
                PaginationRequest(request.itemsPerPage, request.page).normalize()
            )
            val affiliatedProjectsIds = affiliatedProjects.items.map { it.projectId }
            //Seems pretty stupid, but works. If all required resources are available in other project -> list it.
            val wallets = Wallets.retrieveWalletsFromProjects.call(
                RetrieveWalletsForProjectsRequest(affiliatedProjectsIds),
                serviceClient
            ).orThrow()
            val projectIdAndMatchingResources = mutableMapOf<String, Int>()
            val resourcesAppliedFor = application.first.requestedResources.filter { it.creditsRequested!=0L }
            resourcesAppliedFor.forEach {
                val productCategory = it.productCategory
                val productProvider = it.productProvider
                wallets.forEach { wallet ->
                    if (wallet.paysFor.id==productCategory && wallet.paysFor.provider == productProvider) {
                        val value = projectIdAndMatchingResources.getOrDefault(wallet.id, 0)
                        projectIdAndMatchingResources[wallet.id] = value + 1
                    }
                }
            }
            val projectsIdWithRequestedResources = projectIdAndMatchingResources.filter { it.value == resourcesAppliedFor.count() }
            val projectsAvailable = affiliatedProjects.items.filter { projectsIdWithRequestedResources.contains(it.projectId) }
            ok(
                Page(
                    projectsAvailable.size,
                    request.itemsPerPage!!,
                    request.page!!,
                    projectsAvailable
                )
            )
        }

        implement(Grants.uploadLogo) {
            ok(
                settings.uploadLogo(
                    db,
                    ctx.securityPrincipal.toActor(),
                    request.projectId,
                    (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull(),
                    (ctx as HttpCall).call.request.receiveChannel()
                )
            )
        }

        implement(Grants.fetchLogo) {
            val logo = settings.fetchLogo(db, request.projectId)
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

        implement(Grants.uploadDescription) {
            ok(settings.uploadDescription(db, ctx.securityPrincipal.toActor(), request.projectId, request.description))
        }

        implement(Grants.fetchDescription) {
            ok(
                FetchDescriptionResponse(
                    settings.fetchDescription(db, request.projectId)
                )
            )
        }

        implement(Grants.retrieveProducts) {
            val recipient = when (request.recipientType) {
                GrantRecipient.PERSONAL_TYPE -> {
                    GrantRecipient.PersonalProject(request.recipientId)
                }

                GrantRecipient.EXISTING_PROJECT_TYPE -> {
                    GrantRecipient.ExistingProject(request.recipientId)
                }

                GrantRecipient.NEW_PROJECT_TYPE -> {
                    GrantRecipient.NewProject(request.recipientId)
                }

                else -> throw RPCException("Invalid recipientType", HttpStatusCode.BadRequest)
            }

            ok(
                GrantsRetrieveProductsResponse(
                    applications.retrieveProducts(
                        db,
                        ctx.securityPrincipal.toActor(),
                        request.projectId,
                        recipient,
                        request.showHidden
                    )
                )
            )
        }

        return@with
    }
}
