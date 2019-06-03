package dk.sdu.cloud.web

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.serverProvider
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import freemarker.cache.ClassTemplateLoader
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CachingHeaders
import io.ktor.features.ForwardedHeaderSupport
import io.ktor.freemarker.FreeMarker
import io.ktor.freemarker.FreeMarkerContent
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.CachingOptions
import io.ktor.http.content.file
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.date.GMTDate
import org.slf4j.Logger
import java.io.File

class Server(
    override val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    override fun start() {
        val ktorEngine = micro.feature(ServerFeature).ktorApplicationEngine!!
        with (ktorEngine.application) {
            install(FreeMarker) {
                setTemplateExceptionHandler { _, _, _ -> }
                setAttemptExceptionReporter { _, _ -> }

                templateLoader = ClassTemplateLoader(Application::class.java.classLoader, "templates")
            }
            install(CachingHeaders) {
                options { outgoingContent ->
                    when (outgoingContent.contentType?.withoutParameters()) {
                        ContentType.Text.CSS, ContentType.Application.JavaScript -> {
                            CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 60 * 60 * 24 * 7))
                        }

                        else -> null
                    }
                }
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

                file("/favicon.ico", File(staticContent, "favicon.ico"))

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
            }
        }

        startServices()
    }
}
