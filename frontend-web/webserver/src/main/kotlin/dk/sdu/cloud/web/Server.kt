package dk.sdu.cloud.web

import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.service.tokenValidation
import freemarker.cache.ClassTemplateLoader
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ForwardedHeaderSupport
import io.ktor.features.origin
import io.ktor.freemarker.FreeMarker
import io.ktor.freemarker.FreeMarkerContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.files
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.util.date.GMTDate
import io.ktor.util.escapeHTML
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger
import java.io.File

class Server(
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val micro: Micro
) : CommonServer {
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null
    override val log: Logger = logger()

    override fun start() {
        val tokenValidation = micro.tokenValidation

        httpServer = ktor {
            installDefaultFeatures(micro)
            install(ForwardedHeaderSupport)
            install(FreeMarker) {
                setTemplateExceptionHandler { _, _, _ -> }
                setAttemptExceptionReporter { _, _ -> }

                templateLoader = ClassTemplateLoader(Application::class.java.classLoader, "templates")
            }

            val staticContent = listOf("/var/www", "files")
                .map { File(it) }
                .find { it.exists() && it.isDirectory }

            routing {
                static("/assets") {
                    if (staticContent != null) {
                        files(staticContent)
                    }
                }

                get("/app") {
                    call.respondRedirect("/app/dashboard", false)
                }

                get("/app/{...}") {
                    if (staticContent == null) {
                        log.warn("No static content available")
                        call.respond(HttpStatusCode.InternalServerError)
                    } else {
                        call.respondFile(File(staticContent, "index.html"))
                    }
                }

                static("/api/auth-callback") {
                    resources("assets")
                }

                post("/api/auth-callback-csrf") {
                    try {
                        val parameters = call.receiveParameters()
                        val access = parameters["accessToken"]!!
                        val refresh = parameters["refreshToken"]!!
                        val csrf = parameters["csrfToken"]!!

                        tokenValidation.validateOrNull(access) ?: return@post run {
                            log.info("access token is invalid!")
                            call.respond(HttpStatusCode.BadRequest)
                        }

                        val isSecureOrigin = call.request.origin.scheme == "https"

                        call.response.cookies.append(
                            name = "refreshToken",
                            value = refresh,
                            secure = isSecureOrigin,
                            httpOnly = true,
                            expires = GMTDate(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)),
                            path = "/",
                            extensions = mapOf(
                                "SameSite" to "strict"
                            )
                        )


                        call.respond(
                            FreeMarkerContent(
                                "auth-csrf.ftl",
                                mapOf(
                                    "accessToken" to access.escapeHTML(),
                                    "csrfToken" to csrf.escapeHTML()
                                )
                            )
                        )
                    } catch (ex: Exception) {
                        log.debug(ex.stackTraceToString())
                        call.respond(HttpStatusCode.BadRequest)
                    }
                }

                post("/api/auth-callback") {
                    try {
                        val parameters = call.receiveParameters()
                        val access = parameters["accessToken"]!!
                        val refresh = parameters["refreshToken"]!!

                        tokenValidation.validateOrNull(access) ?: return@post run {
                            log.info("access token is invalid!")
                            call.respond(HttpStatusCode.BadRequest)
                        }

                        call.respond(
                            FreeMarkerContent(
                                "auth.ftl",
                                mapOf("accessToken" to access.escapeHTML(), "refreshToken" to refresh.escapeHTML())
                            )
                        )
                    } catch (ex: Exception) {
                        call.respond(HttpStatusCode.BadRequest)
                    }
                }

                static("/api/sync-callback") {
                    resources("assets")
                }

                post("/api/sync-callback") {
                    try {
                        val parameters = call.receiveParameters()
                        val access = parameters["accessToken"]!!
                        val refresh = parameters["refreshToken"]!!

                        tokenValidation.validateOrNull(access) ?: return@post run {
                            log.info("access token is invalid!")
                            call.respond(HttpStatusCode.BadRequest)
                        }

                        call.respond(
                            FreeMarkerContent(
                                "sync.ftl",
                                mapOf("accessToken" to access.escapeHTML(), "refreshToken" to refresh.escapeHTML())
                            )
                        )
                    } catch (ex: Exception) {
                        call.respondText("Bad request", status = HttpStatusCode.BadRequest)
                    }
                }
            }
        }

        startServices()
    }
}
