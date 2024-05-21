package dk.sdu.cloud.web

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.*
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

class Server {
    fun start() {
        val version = File("/var/www/AppVersion.txt").takeIf { it.exists() }?.readText() ?: ""

        embeddedServer(Netty, port = 8080) {
            install(Compression)
            install(ConditionalHeaders) {
                version { call, content ->
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
                options { call, outgoingContent ->
                    when (outgoingContent.contentType?.withoutParameters()) {
                        ContentType.Text.CSS,
                        ContentType.Application.JavaScript,
                        ContentType.Application.OctetStream,
                        ContentType("Application", "x-font-ttf"),
                        ContentType.Image.XIcon,
                        ContentType.Image.SVG
                        -> {
                            CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 60 * 60 * 24))
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

                if (staticContent != null) {
                    staticFiles("/Images", File(staticContent, "Images"))
                    staticFiles("/assets", File(staticContent, "assets"))
                }

                run {
                    val file = File(staticContent, "favicon.ico")
                    get("/favicon.ico") {
                        call.respondFile(file)
                    }
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
