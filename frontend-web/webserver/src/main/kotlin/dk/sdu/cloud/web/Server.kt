package dk.sdu.cloud.web

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CachingHeaders
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
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File

class Server {
    fun start() {
        embeddedServer(Netty, port = 8080) {
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

                get("/status") {
                    call.respondText("OK")
                }

                get("/app") {
                    call.respondRedirect("/app/dashboard", false)
                }

                get("/app/{...}") {
                    if (staticContent == null) {
                        call.respond(HttpStatusCode.InternalServerError)
                    } else {
                        call.respondFile(File(staticContent, "index.html"))
                    }
                }
            }
        }.start(wait = true)
    }
}
