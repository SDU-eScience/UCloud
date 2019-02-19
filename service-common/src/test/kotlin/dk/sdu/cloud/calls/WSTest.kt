package dk.sdu.cloud.calls

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.CallTracing
import dk.sdu.cloud.calls.client.ClientAndBackend
import dk.sdu.cloud.calls.client.FixedOutgoingHostResolver
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingHttpRequestInterceptor
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.calls.server.JobId
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.micro.server
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import java.util.*

data class HelloWorldRequest(val name: String)
data class HelloWorldResponse(val greeting: String)

object WSDescriptions : CallDescriptionContainer("test") {
    private const val baseContext = "/api/ws"

    val helloWorld = call<HelloWorldRequest, HelloWorldResponse, CommonErrorMessage>("helloWorld") {
        auth {
            access = AccessRight.READ
        }

        websocket(baseContext)
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

    with (server) {
        implement(WSDescriptions.helloWorld) {
            ok(HelloWorldResponse("Hello, ${request.name}!"))
        }
    }

    server.start()
}
