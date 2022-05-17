package dk.sdu.cloud.debug

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.File

private object DebuggerInterface : CallDescriptionContainer("debugger") {
    val retrieve = call<Unit, FindByStringId, CommonErrorMessage>("retrieve") {
        httpRetrieve("/api", roles = Roles.PUBLIC)
    }
}

class DebuggerUserInterface(
    private val watchedDirectories: List<String>,
) {
    fun start() {
        val server = RpcServer()
        val engine = embeddedServer(
            Netty,
            port = 42999,
            module = {
                installDefaultFeatures()
            },
            configure = {
                responseWriteTimeoutSeconds = 0
            }
        )
        engine.start(wait = false)

        server.attachRequestInterceptor(IngoingHttpInterceptor(engine, server))
        server.attachRequestInterceptor(IngoingWebSocketInterceptor(engine, server))
        val potentialDirectories = listOf(
            File("./debugger-ui"),
            File("../service-lib-server/debugger-ui"),
            File("./service-lib-server/debugger-ui"),
            File("/var/www/debugger-ui"),
        )

        val baseDir = potentialDirectories.find { it.exists() }
            ?: error("Could not find base directory for assets")

        engine.application.routing {
            static("/assets") {
                files(baseDir)
            }

            get("/") {
                call.respondFile(File(baseDir, "index.html"))
            }
        }

        server.implement(DebuggerInterface.retrieve) {
            ok(FindByStringId("Retrieved"))
        }

        server.start()
    }
}
