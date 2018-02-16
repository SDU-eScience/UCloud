package dk.sdu.cloud.zenodo

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.service.*
import dk.sdu.cloud.zenodo.api.ZenodoAccessRedirectURL
import dk.sdu.cloud.zenodo.api.ZenodoDescriptions
import dk.sdu.cloud.zenodo.api.ZenodoServiceDescription
import dk.sdu.cloud.zenodo.services.ZenodoOAuth
import dk.sdu.cloud.zenodo.services.ZenodoService
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit

class Server(
    private val cloud: AuthenticatedCloud,
    private val kafka: KafkaServices,
    private val configuration: ServerConfiguration,
    private val ktor: HttpServerProvider
) {
    private lateinit var httpServer: ApplicationEngine

    fun start() {
        val instance = ZenodoServiceDescription.instance(configuration.connConfig)
        lateinit var zenodoOauth: ZenodoOAuth
        lateinit var zenodo: ZenodoService

        httpServer = ktor {
            installDefaultFeatures(cloud, kafka, instance, requireJobId = false)
            install(JWTProtection)

            routing {
                route("zenodo") {
                    get("oauth") {
                        val state =
                            call.request.queryParameters["state"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                        val code =
                            call.request.queryParameters["code"] ?: return@get call.respond(HttpStatusCode.BadRequest)

                        val redirectTo = zenodoOauth.requestTokenWithCode(code, state) ?: "/"
                        call.respondRedirect(redirectTo)
                    }
                }

                route("api") {
                    route("zenodo") {
                        protect()

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
                            ok(ZenodoAccessRedirectURL(
                                zenodo.createAuthorizationUrl(who, it.returnTo).toExternalForm())
                            )
                        }
                    }
                }
            }
        }

        httpServer.start(wait = true)
    }

    fun stop() {
        httpServer.stop(gracePeriod = 0, timeout = 30, timeUnit = TimeUnit.SECONDS)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Server::class.java)
    }
}