package dk.sdu.cloud.service

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByIntId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.application.Application
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import kotlin.test.assertEquals

typealias NHttpMethod = io.netty.handler.codec.http.HttpMethod

object MyServiceDescription : ServiceDescription {
    override val name: String = "service"
    override val version: String = "1.0.0"
}

data class SomeData(val data: Int)

object TestDescriptions : RESTDescriptions(MyServiceDescription) {
    val echoId = callDescription<FindByIntId, SomeData, CommonErrorMessage> {
        method = NHttpMethod.POST
        prettyName = "echoId"

        path {
            +"/echo"
        }
        body { bindEntireRequestFromBody() }
    }
}

val simpleEchoServer: Application.() -> Unit = {
    routing {
        RESTServerSupport.allowMissingKafkaHttpLogger = true

        implement(TestDescriptions.echoId) {
            ok(SomeData(it.id))
        }
    }
}

class ImplementFeatureTest {
    @Test
    fun testExtraJSONProperty() {
        withTestApplication(
            moduleFunction = simpleEchoServer,

            test = {
                val response = handleRequest(HttpMethod.Post, "/echo") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    body = """
                        {
                            "id": 1337,
                            "not_present": 42
                        }
                    """.trimIndent()
                }.response

                assertEquals(HttpStatusCode.OK, response.status())
            }
        )
    }

    @Test
    fun testExtraJSONIncorrectType() {
        withTestApplication(
            moduleFunction = simpleEchoServer,

            test = {
                val response = handleRequest(HttpMethod.Post, "/echo") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    body = """
                        {
                            "id": "cannot convert to int",
                            "not_present": 42
                        }
                    """.trimIndent()
                }.response

                assertEquals(HttpStatusCode.BadRequest, response.status())
            }
        )
    }


    @Test
    fun testeValidCall() {

        withTestApplication(
            moduleFunction = simpleEchoServer,

            test = {
                val response = handleRequest(HttpMethod.Post, "/echo") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    body = """
                        {
                            "id": 1337
                        }
                    """.trimIndent()
                }.response

                assertEquals(HttpStatusCode.OK, response.status())
            }
        )
    }
}