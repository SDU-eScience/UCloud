package dk.sdu.cloud.calls

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.bearer
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.calls.server.wsServerConfig
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.micro.server
import io.ktor.http.HttpMethod
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class HelloWorldRequest(val name: String)
data class HelloWorldResponse(val greeting: String)

object WSDescriptions : CallDescriptionContainer("test") {
    private const val baseContext = "/api/ws"

    val helloWorld = call<HelloWorldRequest, HelloWorldResponse, CommonErrorMessage>("helloWorld") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        websocket(baseContext)

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }
}

fun main(args: Array<String>) {
    val description = object : ServiceDescription {
        override val name: String = "test"
        override val version: String = "1.0.0"
    }

    val micro = Micro().apply {
        initWithDefaultFeatures(description, args)
    }

    if (micro.runScriptHandler()) return

    val server = micro.server

    with(server) {
        implement(WSDescriptions.helloWorld) {
            withContext<WSCall> {
                ctx.session.addOnCloseHandler {
                    println("Done")
                }
            }

            ok(HelloWorldResponse("Hello, ${request.name}!"))

            GlobalScope.launch {
                withContext<WSCall> {
                    repeat(5) {
                        delay(1000)

                        println(ctx.bearer)
                        ctx.sendMessage(42)
                    }
                }
            }
        }

        with(WSDescriptions.helloWorld.wsServerConfig) {
            onClose = {
                println("Closing session: $it")
            }
        }

    }

    server.start()
}
