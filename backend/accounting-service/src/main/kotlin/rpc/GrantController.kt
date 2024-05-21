package dk.sdu.cloud.grant.rpc

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.services.grants.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.grant.api.GrantsV2
import dk.sdu.cloud.service.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.ByteArrayInputStream

class GrantController(
    private val grants: GrantsV2Service,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(GrantsV2.browse) {
            ok(grants.browse(actorAndProject, request))
        }

        implement(GrantsV2.retrieve) {
            ok(grants.retrieve(actorAndProject, request.id))
        }

        implement(GrantsV2.submitRevision) {
            ok(grants.submitRevision(actorAndProject, request))
        }

        implement(GrantsV2.updateState) {
            ok(grants.updateState(actorAndProject, request))
        }

        implement(GrantsV2.transfer) {
            ok(grants.transfer(actorAndProject, request))
        }

        implement(GrantsV2.retrieveGrantGivers) {
            var existingProject: String? = null
            var existingApplicationId: String? = null

            when (val r = request) {
                is GrantsV2.RetrieveGrantGivers.Request.ExistingApplication -> {
                    existingApplicationId = r.id
                }

                is GrantsV2.RetrieveGrantGivers.Request.ExistingProject -> {
                    existingProject = r.id
                }

                is GrantsV2.RetrieveGrantGivers.Request.NewProject -> {
                    existingProject = null
                }

                is GrantsV2.RetrieveGrantGivers.Request.PersonalWorkspace -> {
                    existingProject = null
                }
            }

            ok(
                GrantsV2.RetrieveGrantGivers.Response(
                    grants.retrieveGrantGivers(
                        ActorAndProject(actorAndProject.actor, existingProject),
                        existingApplicationId = existingApplicationId,
                    )
                )
            )
        }

        implement(GrantsV2.postComment) {
            ok(grants.postComment(actorAndProject, request))
        }

        implement(GrantsV2.deleteComment) {
            ok(grants.deleteComment(actorAndProject, request))
        }

        implement(GrantsV2.updateRequestSettings) {
            ok(grants.updateRequestSettings(actorAndProject, request))
        }

        implement(GrantsV2.retrieveRequestSettings) {
            ok(grants.retrieveRequestSettings(actorAndProject))
        }

        implement(GrantsV2.retrieveLogo) {
            val logo = grants.retrieveLogo(request.projectId)
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

        implement(GrantsV2.uploadLogo) {
            ok(
                grants.uploadLogo(
                    actorAndProject,
                    (ctx as HttpCall).call.request.header(HttpHeaders.ContentLength)?.toLongOrNull(),
                    (ctx as HttpCall).call.request.receiveChannel()
                )
            )
        }
    }
}
