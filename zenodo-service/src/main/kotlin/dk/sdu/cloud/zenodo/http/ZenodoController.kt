package dk.sdu.cloud.zenodo.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.jobId
import dk.sdu.cloud.calls.server.requiredAuthScope
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.kafka.MappedEventProducer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.zenodo.api.ZenodoAccessRedirectURL
import dk.sdu.cloud.zenodo.api.ZenodoConnectedStatus
import dk.sdu.cloud.zenodo.api.ZenodoDescriptions
import dk.sdu.cloud.zenodo.api.ZenodoPublishCommand
import dk.sdu.cloud.zenodo.api.ZenodoPublishResponse
import dk.sdu.cloud.zenodo.services.PublicationService
import dk.sdu.cloud.zenodo.services.ZenodoRPCService
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.net.URL

private const val TWO_HOURS_IN_MILLS = 1000 * 60 * 60 * 2L

class ZenodoController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val publicationService: PublicationService<DBSession>,
    private val zenodo: ZenodoRPCService,
    private val publishCommandStream: MappedEventProducer<String, ZenodoPublishCommand>,
    private val serviceClient: AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(ZenodoDescriptions.publish) {
            with(ctx as HttpCall) {
                val extensionResponse = AuthDescriptions.tokenExtension.call(
                    TokenExtensionRequest(
                        call.request.bearer!!,
                        listOf(
                            AuthDescriptions.requestOneTimeTokenWithAudience.requiredAuthScope.toString(),
                            FileDescriptions.download.requiredAuthScope.toString()
                        ),
                        TWO_HOURS_IN_MILLS
                    ),
                    serviceClient
                )

                if (extensionResponse !is IngoingCallResponse.Ok) {
                    log.debug("Could not extend token:")
                    log.debug("${extensionResponse.statusCode}")
                    error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                    return@implement
                }

                val extendedJWT = extensionResponse.result.accessToken

                val uploadId =
                    db.withTransaction {
                        publicationService.createUploadForFiles(
                            it,
                            ctx.securityPrincipal.username,
                            request.name,
                            request.filePaths.toSet()
                        )
                    }

                publishCommandStream.emit(ZenodoPublishCommand(extendedJWT, ctx.jobId, uploadId, request))

                ok(ZenodoPublishResponse(uploadId))
            }
        }

        implement(ZenodoDescriptions.requestAccess) {
            val returnTo = URL(request.returnTo)
            if (returnTo.protocol !in ALLOWED_PROTOCOLS || returnTo.host !in ALLOWED_HOSTS) {
                error(CommonErrorMessage("Bad Request"), HttpStatusCode.BadRequest)
                return@implement
            }

            ok(
                ZenodoAccessRedirectURL(
                    zenodo.createAuthorizationUrl(ctx.securityPrincipal.username, request.returnTo).toExternalForm()
                )
            )
        }

        implement(ZenodoDescriptions.status) {
            ok(ZenodoConnectedStatus(zenodo.isConnected(ctx.securityPrincipal.username)))
        }

        implement(ZenodoDescriptions.listPublications) {
            ok(db.withTransaction {
                publicationService.findForUser(it, ctx.securityPrincipal.username, request.normalize())
            })
        }

        implement(ZenodoDescriptions.findPublicationById) {
            ok(db.withTransaction {
                publicationService.findById(it, ctx.securityPrincipal.username, request.id)
            })
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ZenodoController::class.java)

        @Suppress("ObjectPropertyNaming")
        private val ALLOWED_PROTOCOLS = setOf("http", "https")

        @Suppress("ObjectPropertyNaming")
        private val ALLOWED_HOSTS = setOf("localhost", "cloud.sdu.dk")
    }
}
