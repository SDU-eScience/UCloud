package dk.sdu.cloud.zenodo.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionRequest
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.zenodo.api.*
import dk.sdu.cloud.zenodo.services.PublicationService
import dk.sdu.cloud.zenodo.services.ZenodoRPCService
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import org.slf4j.LoggerFactory
import java.net.URL

class ZenodoController<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val publicationService: PublicationService<DBSession>,
    private val zenodo: ZenodoRPCService,
    private val publishCommandStream: MappedEventProducer<String, ZenodoPublishCommand>
) : Controller {
    override val baseContext = ZenodoDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(ZenodoDescriptions.publish) { req ->
            logEntry(log, req)
            val extensionResponse = AuthDescriptions.tokenExtension.call(
                TokenExtensionRequest(
                    call.request.bearer!!,
                    listOf(
                        AuthDescriptions.requestOneTimeTokenWithAudience.requiredAuthScope.toString(),
                        FileDescriptions.download.requiredAuthScope.toString()
                    ),
                    1000 * 60 * 60 * 2L
                ),
                call.cloudClient
            )

            if (extensionResponse !is RESTResponse.Ok) {
                log.debug("Could not extend token:")
                log.debug("${extensionResponse.status} : ${extensionResponse.rawResponseBody}")
                error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                return@implement
            }

            val extendedJWT = extensionResponse.result.accessToken

            val uploadId =
                db.withTransaction {
                    publicationService.createUploadForFiles(
                        it,
                        call.securityPrincipal.username,
                        req.name,
                        req.filePaths.toSet()
                    )
                }

            publishCommandStream.emit(ZenodoPublishCommand(extendedJWT, call.request.jobId, uploadId, req))

            ok(ZenodoPublishResponse(uploadId))
        }

        implement(ZenodoDescriptions.requestAccess) {
            logEntry(log, it)

            val returnTo = URL(it.returnTo)
            if (returnTo.protocol !in ALLOWED_PROTOCOLS || returnTo.host !in ALLOWED_HOSTS) {
                error(CommonErrorMessage("Bad Request"), HttpStatusCode.BadRequest)
                return@implement
            }

            ok(
                ZenodoAccessRedirectURL(
                    zenodo.createAuthorizationUrl(call.securityPrincipal.username, it.returnTo).toExternalForm()
                )
            )
        }

        implement(ZenodoDescriptions.status) {
            logEntry(log, it)
            ok(ZenodoConnectedStatus(zenodo.isConnected(call.securityPrincipal.username)))
        }

        implement(ZenodoDescriptions.listPublications) { req ->
            logEntry(log, req)

            ok(db.withTransaction {
                publicationService.findForUser(it, call.securityPrincipal.username, req.normalize())
            })
        }

        implement(ZenodoDescriptions.findPublicationById) { req ->
            logEntry(log, req)

            ok(db.withTransaction {
                publicationService.findById(it, call.securityPrincipal.username, req.id)
            })
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ZenodoController::class.java)

        private val ALLOWED_PROTOCOLS = setOf("http", "https")
        private val ALLOWED_HOSTS = setOf("localhost", "cloud.sdu.dk")
    }
}