package dk.sdu.cloud.application

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.*
import io.ktor.server.netty.Netty
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

var developmentBuild = true

fun template(head: HEAD.() -> Unit = {}, body: BODY.() -> Unit = {}): String {
    return createHTML().html {
        head {
            title("UCloud Debugger")

            head()
        }

        body {
            link(rel = "stylesheet", href = "/static/big-json-viewer/default.css")
            script(src = "/static/big-json-viewer/browser-api.js") {}

            // HACK(Dan): Kotlin/JS has a super confusing way of using continuous builds. I am
            // pretty sure this is _not_ the way you are supposed to do it. But they definitely
            // don't make it obvious how to do it.
            //
            // When developmentBuild = true, then you can just run the frontend with
            // `gradle jsBrowserDistribution --continuous`. This should work, but you will need to add
            // any new packages here.
            /*
            script(src = "/static/js/packages_imported/kotlin/1.7.10/kotlin.js") {}
            script(src = "/static/js/packages/debugger/kotlin/kotlin-kotlin-stdlib-js-ir.js") {}
            script(src = "/static/js/packages/debugger/kotlin/kotlin_kotlin.js") {}
            script(src = "/static/js/packages/debugger/kotlin/kotlin_org_jetbrains_kotlinx_kotlinx_html.js") {}
            script(src = "/static/js/packages/debugger/kotlin/kotlin_org_jetbrains_kotlinx_kotlinx_serialization_core.js") {}
            script(src = "/static/js/packages/debugger/kotlin/kotlin_org_jetbrains_kotlinx_kotlinx_serialization_json.js") {}
            script(src = "/static/js/packages/debugger/kotlin/kotlinx-serialization-kotlinx-serialization-core-js-ir.js") {}
            script(src = "/static/js/packages/debugger/kotlin/kotlinx-serialization-kotlinx-serialization-json-js-ir.js") {}
            */
            script(src = "/static/debugger.js") {}

            body()
        }
    }
}

fun main(args: Array<String>) {
    val host = args.getOrNull(0) ?: "127.0.0.1"
    val port = args.getOrNull(1)?.toIntOrNull() ?: 42999
    val logFolder = args.getOrNull(2) ?: "./logs"
    developmentBuild = !args.contains("--static")
    println(args.toList())
    val sessionManager = SessionManager(listOf(logFolder))
    sessionManager.start()

    embeddedServer(Netty, port = port, host = host) {
        install(WebSockets)

        routing {
            get("/") {
                call.respondText(ContentType.Text.Html, HttpStatusCode.OK) {
                    template(
                        body = {
                        }
                    )
                }
            }

            get("/popup") {
                call.respondText(ContentType.Text.Html, HttpStatusCode.OK) {
                    template()
                }
            }

            webSocket("/") {
                sessionManager.registerAndHandle(this)
            }

            static("/static") {
                files("./assets")
                if (!developmentBuild) {
                    files("./build/distributions")
                    resources()
                } else {
                    resources()
                }
            }
        }
    }.start(wait = true)
}
