package dk.sdu.cloud.zenodo.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
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
        protect()

        implement(ZenodoDescriptions.publish) { req ->
            logEntry(log, req)
            val jwt = call.request.validatedPrincipal

            val uploadId =
                db.withTransaction {
                    publicationService.createUploadForFiles(
                        it,
                        call.request.currentUsername,
                        req.name,
                        req.filePaths.toSet()
                    )
                }

            publishCommandStream.emit(ZenodoPublishCommand(jwt.token, call.request.jobId, uploadId, req))

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
                    zenodo.createAuthorizationUrl(call.request.currentUsername, it.returnTo).toExternalForm()
                )
            )
        }

        implement(ZenodoDescriptions.status) {
            logEntry(log, it)
            ok(ZenodoConnectedStatus(zenodo.isConnected(call.request.currentUsername)))
        }

        implement(ZenodoDescriptions.listPublications) { req ->
            logEntry(log, req)

            ok(db.withTransaction {
                publicationService.findForUser(it, call.request.currentUsername, req.normalize())
            })
        }

        implement(ZenodoDescriptions.findPublicationById) { req ->
            logEntry(log, req)

            ok(db.withTransaction {
                publicationService.findById(it, call.request.currentUsername, req.id)
            })
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ZenodoController::class.java)

        private val ALLOWED_PROTOCOLS = setOf("http", "https")
        private val ALLOWED_HOSTS = setOf("localhost", "cloud.sdu.dk")
    }
}