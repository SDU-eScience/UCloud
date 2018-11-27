package dk.sdu.cloud.service

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByIntId
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTCallDescription
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.service.test.KafkaMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.assertMessageThat
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.messagesForTopic
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serde
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object MyServiceDescription : ServiceDescription {
    override val name: String = "service"
    override val version: String = "1.0.0"
}

val AUDIT_STRING = "audit"

data class SomeData(val data: Int)

data class SomeAudit(val audit: String)

object TestDescriptions : RESTDescriptions("echo") {
    val echoId = callDescription<FindByIntId, SomeData, CommonErrorMessage> {
        method = HttpMethod.Post
        name = "echoId"

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        path {
            +"echo"
        }
        body { bindEntireRequestFromBody() }
    }

    val echoWithAudit = callDescriptionWithAudit<FindByIntId, SomeData, CommonErrorMessage, SomeAudit> {
        method = HttpMethod.Post
        name = "echoAudit"

        auth {
            roles = Roles.PUBLIC
            access = AccessRight.READ
        }

        path {
            +"echo"
            +"audit"
        }

        body { bindEntireRequestFromBody() }
    }
}

val simpleEchoServer: KtorApplicationTestSetupContext.() -> List<Controller> = {
    listOf(object : Controller {
        override val baseContext: String = "/"
        override fun configure(routing: Route): Unit = with(routing) {
            implement(TestDescriptions.echoId) {
                ok(SomeData(it.id))
            }

            implement(TestDescriptions.echoWithAudit) {
                audit(SomeAudit(AUDIT_STRING))
                ok(SomeData(it.id))
            }
        }
    })
}

class ImplementFeatureTest {
    @Test
    fun testExtraJSONProperty() {
        withKtorTest(
            setup = simpleEchoServer,

            test = {
                with(engine) {
                    val response = handleRequest(HttpMethod.Post, "/echo") {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(
                            """
                        {
                            "id": 1337,
                            "not_present": 42
                        }
                        """.trimIndent()
                        )
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                }
            }
        )
    }

    @Test
    fun testExtraJSONIncorrectType() {
        withKtorTest(
            setup = simpleEchoServer,

            test = {
                with(engine) {
                    val response = handleRequest(HttpMethod.Post, "/echo") {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(
                            """
                        {
                            "id": "cannot convert to int",
                            "not_present": 42
                        }
                        """.trimIndent()
                        )
                    }.response

                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            }
        )
    }


    @Test
    fun testValidCall() {
        withKtorTest(
            setup = simpleEchoServer,

            test = {
                with(engine) {
                    val response = handleRequest(HttpMethod.Post, "/echo") {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        setBody(""" { "id": 1337 } """)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                }
            }
        )
    }

    @Test
    fun `test kafka audit`() {
        withKtorTest(
            setup = simpleEchoServer,
            test = {
                with(engine) {
                    val response = handleRequest(HttpMethod.Post, "/echo") {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        addHeader("Job-Id", "1234")
                        setBody(""" { "id": 1337 } """)
                    }.response

                    Thread.sleep(1000) // Audit streams are async to the request. We need to wait for them.

                    assertEquals(HttpStatusCode.OK, response.status())

                    val httpStream = KafkaHttpLogger.httpLogsStream

                    KafkaMock.assertMessageThat(httpStream) {
                        @Suppress("UNCHECKED_CAST")
                        TestDescriptions.echoId.fullName == it.requestName &&
                                1337 == (it.requestJson as? Map<String, Any>)?.get("id")
                    }

                    assertThatPropertyEquals(KafkaMock.messagesForTopic(TestDescriptions.auditStream), { it.size }, 1)
                }
            }
        )
    }

    @Test
    fun `test kafka normalized audit`() {
        withKtorTest(
            setup = simpleEchoServer,
            test = {
                with(engine) {

                    val response = handleRequest(HttpMethod.Post, "/echo/audit") {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        addHeader("Job-Id", "1234")
                        setBody(""" { "id": 1337 } """)
                    }.response

                    Thread.sleep(250) // Audit streams are async to the request. We need to wait for them.

                    assertEquals(HttpStatusCode.OK, response.status())

                    val httpStream = KafkaHttpLogger.httpLogsStream

                    KafkaMock.assertMessageThat(httpStream) {
                        @Suppress("UNCHECKED_CAST")
                        TestDescriptions.echoWithAudit.fullName == it.requestName &&
                                "POST" == it.httpMethod
                    }

                    assertThatPropertyEquals(KafkaMock.messagesForTopic(TestDescriptions.auditStream), { it.size }, 1)

                }
            }
        )
    }

    @Test
    fun `test audit deserialization of multiple messages`() {
        // TODO Should probably move this to another file. But just one test (for now)
        withKtorTest(
            setup = simpleEchoServer,
            test = {
                with(engine) {
                    val requestId = 1337

                    // Send two different messages
                    val response1 = handleRequest(HttpMethod.Post, "/echo") {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        addHeader("Job-Id", "1234")
                        setBody(""" { "id": $requestId } """)
                    }.response
                    assertEquals(HttpStatusCode.OK, response1.status())

                    val response2 = handleRequest(HttpMethod.Post, "/echo/audit") {
                        addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        addHeader("Job-Id", "1234")
                        setBody(""" { "id": $requestId } """)
                    }.response
                    assertEquals(HttpStatusCode.OK, response2.status())

                    // Still need to wait for the messages to appear
                    Thread.sleep(250)

                    // Retrieve all audit messages (from mocked producer)
                    val httpStream = KafkaHttpLogger.httpLogsStream
                    assertThatPropertyEquals(KafkaMock.messagesForTopic(TestDescriptions.auditStream), { it.size }, 2)

                    // We track that both records have been hit
                    var echoWithAuditRecorded = false
                    var echoWithoutAuditRecorded = false

                    // Run the parser, like an ordinary consumer would
                    for (record in KafkaMock.messagesForTopic(TestDescriptions.auditStream)) {

                        KafkaMock.assertMessageThat(httpStream) {
                            @Suppress("UNCHECKED_CAST")
                            TestDescriptions.echoWithAudit.fullName == it.requestName &&
                                    "POST" == it.httpMethod
                        }
                        echoWithAuditRecorded = true

                        KafkaMock.assertMessageThat(httpStream) {
                            @Suppress("UNCHECKED_CAST")
                            TestDescriptions.echoId.fullName == it.requestName &&
                                    1337 == (it.requestJson as? Map<String, Any>)?.get("id")
                        }
                        echoWithoutAuditRecorded = true
                    }

                    assertTrue(echoWithAuditRecorded)
                    assertTrue(echoWithoutAuditRecorded)
                }
            }
        )
    }


    private fun <A : Any> deserializeAudit(
        record: ProducerRecord<String, String>,
        description: RESTCallDescription<*, *, *, A>
    ): AuditEvent<A> {
        val tree = defaultMapper.readTree(record.value())
        return description.parseAuditMessageOrNull(tree)!!
    }

    private fun <T> deserialize(serde: Serde<T>, record: ProducerRecord<String, String>): T {
        return serde.deserializer().deserialize(record.topic(), record.value().toByteArray())
    }
}
