package dk.sdu.cloud.application

import io.ktor.application.call
import io.ktor.html.respondHtml
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.*
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.html.*

const val developmentBuild = true
fun main() {
    embeddedServer(Netty, port = 42999, host = "127.0.0.1") {
        routing {
            get("/") {
                call.respondHtml(HttpStatusCode.OK) {
                    head {
                        title("UCloud Debugger")
                    }

                    body {
                        link(rel = "stylesheet", href = "/static/big-json-viewer/default.css")
                        script(src = "/static/big-json-viewer/browser-api.js") {}
                        if (!developmentBuild) {
                            script(src = "/static/debugger.js") {}
                        } else {
                            // HACK(Dan): Kotlin/JS has a super confusing way of using continuous builds. I am
                            // pretty sure this is _not_ the way you are supposed to do it. But they definitely
                            // don't make it obvious how to do it.
                            //
                            // When developmentBuild = true, then you can just run the frontend with
                            // `gradle jsBrowserDistribution --continuous`. This should work, but you will need to add
                            // any new packages here.
                            script(src = "/static/js/packages_imported/kotlin/1.6.21/kotlin.js") {}
                            script(src = "/static/js/packages/debugger/kotlin/kotlin_kotlin.js") {}
                            script(src = "/static/js/packages/debugger/kotlin/kotlin_org_jetbrains_kotlinx_kotlinx_html.js") {}
                            script(src = "/static/js/packages/debugger/kotlin/kotlin_org_jetbrains_kotlinx_kotlinx_serialization_core.js") {}
                            script(src = "/static/js/packages/debugger/kotlin/kotlin_org_jetbrains_kotlinx_kotlinx_serialization_json.js") {}
                            script(src = "/static/js/packages/debugger/kotlin/debugger.js") {}
                        }
                    }
                }
            }
            static("/static") {
                files("./assets")
                if (!developmentBuild) {
                    resources()
                } else {
                    files("./build")
                    resources()
                }
            }
        }
    }.start(wait = true)
}
