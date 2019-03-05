package dk.sdu.cloud.calls

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.CallTracing
import dk.sdu.cloud.calls.client.ClientAndBackend
import dk.sdu.cloud.calls.client.FixedOutgoingHostResolver
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingHttpRequestInterceptor
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.server.JobId
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.causedBy
import dk.sdu.cloud.calls.server.jobId
import dk.sdu.cloud.calls.types.StreamingFile
import dk.sdu.cloud.calls.types.StreamingRequest
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.micro.server
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.io.readUTF8Line
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.*

data class Foo(val foo: Int)

data class SimpleRequest(val foo: Foo, val path: String, val query: String)
data class SimpleResponse(val bar: Int)
data class SimpleAudit(val baz: Int)

enum class EnumType {
    A, B, C
}

data class SRequest(
    val a: Int,
    val b: Boolean,
    val streamingOne: StreamingFile?,
    val c: String?,
    val d: EnumType?,
    val streamingTwo: StreamingFile?
)

data class SResponse(val hello: String)

object TestDescriptions : CallDescriptionContainer("test") {
    val baseContext = "/api/test"

    val simple = call<SimpleRequest, SimpleResponse, CommonErrorMessage>("simple") {
        auth {
            access = AccessRight.READ
        }

        audit<SimpleAudit>()

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"simple"
                +boundTo(SimpleRequest::path)
            }

            params {
                +boundTo(SimpleRequest::query)
            }

            body { bindToSubProperty(SimpleRequest::foo) }
        }
    }

    val streaming = call<StreamingRequest<SRequest>, SResponse, CommonErrorMessage>("streaming") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"streaming"
            }

            body { bindEntireRequestFromBody() }
        }
    }

    data class NRequest(
        val path1: String,
        val path2: String?,
        val query1: Int,
        val query2: Int?,
        val body: Foo?
    )

    data class NResponse(
        val bar: Int
    )

    val nullability = call<NRequest, NResponse, CommonErrorMessage>("nullability") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"null"
                +boundTo(NRequest::path1)
                +boundTo(NRequest::path2)
            }

            params {
                +boundTo(NRequest::query1)
                +boundTo(NRequest::query2)
            }

            body { bindToSubProperty(NRequest::body) }
        }
    }

    val ping = call<Unit, Unit, CommonErrorMessage>("ping") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"ping"
            }
        }
    }

    val pong = call<Unit, Unit, CommonErrorMessage>("pong") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"pong"
            }

            headers {
                +"X-Required"
            }
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

    val client = RpcClient()
    client.attachFilter(CallTracing())
    OutgoingHttpRequestInterceptor()
        .install(
            client,
            FixedOutgoingHostResolver(HostInfo("localhost", port = 8080))
        )

    val httpClient = ClientAndBackend(client, OutgoingHttpCall)
    val authenticatedClient = AuthenticatedClient(client, OutgoingHttpCall, authenticator = {
        (it as OutgoingHttpCall).builder.header(HttpHeaders.JobId, UUID.randomUUID().toString())
    })

    val server = micro.server

    with(server) {
        implement(TestDescriptions.simple) {
            audit(SimpleAudit(request.foo.foo))

            ok(SimpleResponse(request.foo.foo))
        }

        implement(TestDescriptions.streaming) {
            val request = request.asIngoing()
            request.receiveBlocks { block ->
                println(block)
                if (block.streamingOne != null) {
                    println(block.streamingOne.channel.readUTF8Line())
                } else if (block.streamingTwo != null) {
                    println(block.streamingTwo.channel.readUTF8Line())
                }
            }

            ok(SResponse("Hi!"))
        }

        implement(TestDescriptions.nullability) {
            ok(TestDescriptions.NResponse(42))
        }

        implement(TestDescriptions.ping) {
            println(ctx.jobId)
            coroutineScope {
                async {
                    TestDescriptions.pong.call(Unit, authenticatedClient)
                }.join()
            }
            ok(Unit)
        }

        implement(TestDescriptions.pong) {
            println(ctx.causedBy)
            ok(Unit)
        }
    }

    server.start()

    println("Yes!")

    runBlocking {
        /*
        TestDescriptions.simple.call(SimpleRequest(Foo(42), "path", "query"), authenticatedClient)

        val f1 = Files.createTempFile("", "").toFile().also { it.writeText("hi") }
        val f2 = Files.createTempFile("", "").toFile().also { it.writeText("ba") }

        TestDescriptions.streaming.call(
            StreamingRequest.Outgoing(
                SRequest(
                    42,
                    true,
                    StreamingFile.fromFile(f1),
                    "hi",
                    EnumType.A,
                    StreamingFile.fromFile(f2)
                )
            ),
            authenticatedClient
        )

        TestDescriptions.nullability.call(
            TestDescriptions.NRequest(
                path1 = "p1",
                path2 = null,
                query1 = 42,
                query2 = null,
                body = null
            ),
            authenticatedClient
        )

        TestDescriptions.nullability.call(
            TestDescriptions.NRequest(
                path1 = "p1",
                path2 = "p2",
                query1 = 42,
                query2 = null,
                body = null
            ),
            authenticatedClient
        )

        TestDescriptions.nullability.call(
            TestDescriptions.NRequest(
                path1 = "p1",
                path2 = null,
                query1 = 42,
                query2 = 1337,
                body = null
            ),
            authenticatedClient
        )

        TestDescriptions.nullability.call(
            TestDescriptions.NRequest(
                path1 = "p1",
                path2 = null,
                query1 = 42,
                query2 = null,
                body = Foo(42)
            ),
            authenticatedClient
        )
        */

        TestDescriptions.ping.call(Unit, authenticatedClient)
    }
}

