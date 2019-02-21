package dk.sdu.cloud.calls

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.client.FixedOutgoingHostResolver
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHostResolverInterceptor
import dk.sdu.cloud.calls.client.OutgoingWSRequestInterceptor
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.TYPE_PROPERTY
import io.ktor.http.HttpMethod

data class HelloWorldRequest(val name: String)
data class HelloWorldResponse(val greeting: String)

// TODO Create sealed and make it work here
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = LongRunningResponse.Timeout::class, name = "timeout"),
    JsonSubTypes.Type(value = LongRunningResponse.Result::class, name = "result")
)
sealed class LongRunningResponse<T> {
    data class Timeout<T>(
        val why: String = "The operation has timed out and will continue in the background"
    ) : LongRunningResponse<T>()

    data class Result<T>(
        val item: T
    ) : LongRunningResponse<T>()
}

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

    val bug = call<Unit, LongRunningResponse<Int>, CommonErrorMessage>("bug") {
        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        websocket(baseContext)
    }
}

fun main(args: Array<String>) {
    if (true) {
        val jacksonTypeRef = jacksonTypeRef<LongRunningResponse<Int>>()
        val node = defaultMapper.readerFor(jacksonTypeRef).readTree(
            defaultMapper
                .writerFor(jacksonTypeRef)
                .writeValueAsString(LongRunningResponse.Result(42))
        )

        println(
            defaultMapper.writeValueAsString(
                mapOf<String, Any>(
                    WSMessage.STATUS_FIELD to 200,
                    WSMessage.PAYLOAD_FIELD to node
                )
            )
        )
        return
    }

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

        implement(WSDescriptions.bug) {
            ok(LongRunningResponse.Result(42))
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

    /*
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
    */
}
