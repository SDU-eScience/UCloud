package dk.sdu.cloud.zenodo.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.*
import dk.sdu.cloud.zenodo.api.*
import dk.sdu.cloud.zenodo.services.*
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory
import java.net.URL

class ZenodoController(
    kafka: KafkaServices,
    private val publicationService: PublicationService,
    private val zenodo: ZenodoRPCService
) {
    private val publishCommandStream = kafka.producer.forStream(ZenodoCommandStreams.publishCommands)

    fun configure(routing: Route) = with(routing) {
        route("/api/zenodo") {
            protect()

            implement(ZenodoDescriptions.publish) {
                logEntry(log, it)
                val jwt = call.request.validatedPrincipal

                try {
                    val uploadId = publicationService.createUploadForFiles(jwt, it.name, it.filePaths.toSet())
                    publishCommandStream.emit(ZenodoPublishCommand(jwt.token, call.request.jobId, uploadId, it))
                    ok(ZenodoPublishResponse(uploadId))
                } catch (ex: PublicationException) {
                    error(ZenodoErrorMessage(ex.connected, ex.message), ex.recommendedStatusCode)
                }
            }

            implement(ZenodoDescriptions.requestAccess) {
                logEntry(log, it)
                val returnToURL = URL(it.returnTo)
                if (returnToURL.protocol !in setOf("http", "https")) {
                    error(CommonErrorMessage("Bad Request"), HttpStatusCode.BadRequest)
                    return@implement
                }

                if (returnToURL.host !in setOf("localhost", "cloud.sdu.dk")) {
                    // TODO This should be handled in a more generic way
                    error(CommonErrorMessage("Bad Request"), HttpStatusCode.BadRequest)
                    return@implement
                }

                val who = call.request.validatedPrincipal
                ok(
                    ZenodoAccessRedirectURL(
                        zenodo.createAuthorizationUrl(who, it.returnTo).toExternalForm()
                    )
                )
            }

            implement(ZenodoDescriptions.status) {
                logEntry(log, it)

                ok(ZenodoConnectedStatus(zenodo.isConnected(call.request.validatedPrincipal)))
            }

            implement(ZenodoDescriptions.listPublications) {
                logEntry(log, it)

                try {
                    zenodo.validateToken(call.request.validatedPrincipal)
                } catch (ex: MissingOAuthToken) {
                    error(ZenodoErrorMessage(false, "Not connected to Zenodo"), HttpStatusCode.BadRequest)
                    return@implement
                } catch (ex: TooManyRetries) {
                    error(ZenodoErrorMessage(false, "Could not connect to Zenodo"), HttpStatusCode.BadGateway)
                    return@implement
                }

                try {
                    ok(publicationService.findForUser(call.request.validatedPrincipal, it.pagination))
                } catch (ex: PublicationException) {
                    error(ZenodoErrorMessage(ex.connected, ex.message), ex.recommendedStatusCode)
                }
            }

            implement(ZenodoDescriptions.findPublicationById) {
                logEntry(log, it)

                try {
                    ok(publicationService.findById(call.request.validatedPrincipal, it.id))
                } catch (ex: PublicationException) {
                    error(ZenodoErrorMessage(ex.connected, ex.message), ex.recommendedStatusCode)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ZenodoController::class.java)
    }
}