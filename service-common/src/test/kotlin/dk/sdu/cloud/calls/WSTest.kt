package dk.sdu.cloud.calls

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.client.ClientAndBackend
import dk.sdu.cloud.calls.client.FixedOutgoingHostResolver
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHostResolverInterceptor
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.calls.client.OutgoingWSRequestInterceptor
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.subscribe
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.micro.server
import io.ktor.http.HttpMethod
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.system.measureTimeMillis

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
            /*
            coroutineScope {
                launch {
                    withContext<WSCall> {
                        repeat(5) {
                            delay(1000)
                            sendWSMessage(ctx, HelloWorldResponse("Hi!"))
                        }
                    }
                }
            }
            */

            ok(HelloWorldResponse("Hello, ${request.name}!"))
        }

        server.start()
    }

    val client = RpcClient()
    client.attachFilter(
        OutgoingHostResolverInterceptor(
            FixedOutgoingHostResolver(
                HostInfo(
                    host = "localhost",
                    port = 8080,
                    scheme = "ws"
                )
            )
        )
    )
    client.attachRequestInterceptor(OutgoingWSRequestInterceptor())

    runBlocking {
        GlobalScope.launch {
            val clientAndBackend = ClientAndBackend(client, OutgoingWSCall)
            coroutineScope {
                /*
                WSDescriptions.helloWorld.subscribe(
                    HelloWorldRequest("Dan"),
                    clientAndBackend.bearerAuth(""),
                    handler = {
                        println(it)
                    }
                )
                */

                val time = measureTimeMillis {
                    (1..100_000).map {
//                        launch {
                            WSDescriptions.helloWorld.call(
                                HelloWorldRequest("Dan"),
                                clientAndBackend
                            )
//                        }
                    }//.joinAll()
                }

                println("It took $time")
            }
        }
    }
}
