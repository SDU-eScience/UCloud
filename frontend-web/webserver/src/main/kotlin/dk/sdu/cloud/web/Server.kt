package dk.sdu.cloud.web

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.*
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.response.respondText
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.File

class Server {
    fun start() {
        val version = File("/var/www/AppVersion.txt").takeIf { it.exists() }?.readText() ?: ""

        embeddedServer(Netty, port = 8080) {
            install(Compression)
            install(ConditionalHeaders) {
                version { content ->
                    if (content is LocalFileContent) {
                        when (content.contentType) {
                            ContentType.Text.CSS,
                            ContentType.Application.JavaScript,
                            ContentType.Application.OctetStream,
                            ContentType("Application", "x-font-ttf"),
                            ContentType.Image.XIcon,
                            ContentType.Image.SVG -> {
                                listOf(EntityTagVersion("W/${content.file.path}_${version}"))
                            }

                            else -> emptyList()
                        }

                    } else {
                        emptyList()
                    }
                }
            }
            install(CachingHeaders) {
                options { outgoingContent ->
                    when (outgoingContent.contentType?.withoutParameters()) {
                        ContentType.Text.CSS,
                        ContentType.Application.JavaScript,
                        ContentType.Application.OctetStream,
                        ContentType("Application", "x-font-ttf"),
                        ContentType.Image.XIcon,
                        ContentType.Image.SVG
                        -> {
                            CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 60 * 60 * 24 * 365))
                        }

                        else -> null
                    }
                }
            }

            val staticContent = listOf("/var/www", "files")
                .map { File(it) }
                .find { it.exists() && it.isDirectory }

            routing {
                get("/AppVersion.txt") {
                    call.respondText(version)
                }

                get("/assets/Assets/AppVersion.txt") {
                    call.respondText(version)
                }

                static("/assets") {
                    if (staticContent != null) {
                        files(File(staticContent, "assets"))
                    }
                }

                file("/favicon.ico", File(staticContent, "favicon.ico"))

                /*
                get("/status") {
                    call.respondText("OK")
                }
                */

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
