package dk.sdu.cloud.web

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import freemarker.cache.ClassTemplateLoader
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.content.files
import io.ktor.content.resources
import io.ktor.content.static
import io.ktor.freemarker.FreeMarker
import io.ktor.freemarker.FreeMarkerContent
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.util.escapeHTML
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger
import java.io.File

class Server(
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val instance: ServiceInstance
) : CommonServer {
    override lateinit var httpServer: ApplicationEngine
    override val kStreams: KafkaStreams? = null
    override val log: Logger = logger()

    override fun start() {
        httpServer = ktor {
            installDefaultFeatures(cloud, kafka, instance, requireJobId = false)
            install(FreeMarker) {
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

                post("/api/auth-callback") {
                    try {
                        val parameters = call.receiveParameters()
                        val access = parameters["accessToken"]!!
                        val refresh = parameters["refreshToken"]!!
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
