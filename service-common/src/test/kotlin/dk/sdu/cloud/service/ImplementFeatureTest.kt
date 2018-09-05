package dk.sdu.cloud.service

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByIntId
import dk.sdu.cloud.client.*
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Serde
import org.junit.Test
import java.util.*
import java.util.concurrent.FutureTask
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
        prettyName = "echoId"

        path {
            +"echo"
        }
        body { bindEntireRequestFromBody() }
    }

    val echoWithAudit = callDescriptionWithAudit<FindByIntId, SomeData, CommonErrorMessage, SomeAudit> {
        method = HttpMethod.Post
        prettyName = "echoAudit"

        path {
            +"echo"
            +"audit"
        }

        body { bindEntireRequestFromBody() }
    }
}

val records = ArrayList<ProducerRecord<String, String>>()
val mockedKafkaProducer = mockk<KafkaProducer<String, String>>(relaxed = true).also { mockedKafkaProducer ->
    every {
        mockedKafkaProducer.send(
            capture(records),
            any()
        )
    } answers {
        val result = FutureTask { mockk<RecordMetadata>(relaxed = true) }
        val callback = args.last() as Callback
        callback.onCompletion(mockk(relaxed = true), null)
        result
    }
}
val kafkaService = KafkaServices(Properties(), Properties(), mockedKafkaProducer, mockk(relaxed = true))

val simpleEchoServer: Application.() -> Unit = {
    install(KafkaHttpLogger) {
        kafka = kafkaService
        serverDescription = ServiceInstance(MyServiceDescription.definition(), "localhost", 8080)
    }

    install(ContentNegotiation) {
        jackson { }
    }

    interceptJobId(false)

    routing {
        RESTServerSupport.allowMissingKafkaHttpLogger = false
        implement(TestDescriptions.echoId) {
            ok(SomeData(it.id))
        }

        implement(TestDescriptions.echoWithAudit) {
            audit(SomeAudit(AUDIT_STRING))
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
        )
    }

    @Test
    fun testExtraJSONIncorrectType() {
        withTestApplication(
            moduleFunction = simpleEchoServer,

            test = {
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
        )
    }


    @Test
    fun testValidCall() {
        withTestApplication(
            moduleFunction = simpleEchoServer,

            test = {
                val response = handleRequest(HttpMethod.Post, "/echo") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(""" { "id": 1337 } """)
                }.response

                assertEquals(HttpStatusCode.OK, response.status())
            }
        )
    }

    @Test
    fun `test kafka audit`() {
        withTestApplication(
            moduleFunction = simpleEchoServer,
            test = {
                records.clear()

                val response = handleRequest(HttpMethod.Post, "/echo") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader("Job-Id", "1234")
                    setBody(""" { "id": 1337 } """)
                }.response

                Thread.sleep(250) // Audit streams are async to the request. We need to wait for them.

                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(2, records.size)

                val httpStream = KafkaHttpLogger.httpLogsStream
                val httpEvent =
                    records.find { it.topic() == httpStream.name } ?: throw AssertionError("http event missing")
                val deserializedHttpEvent = deserialize(httpStream.valueSerde, httpEvent)

                val auditStream = TestDescriptions.auditStream
                val auditEvent =
                    records.find { it.topic() == auditStream.name } ?: throw AssertionError("audit missing")
                val deserializedAuditEvent = deserializeAudit(auditEvent, TestDescriptions.echoId)


                assertEquals(TestDescriptions.echoId.fullName, deserializedHttpEvent.requestName)
                assertEquals("POST", deserializedAuditEvent.http.httpMethod.toUpperCase())
                assertEquals(FindByIntId(1337), deserializedAuditEvent.request)
            }
        )
    }

    @Test
    fun `test kafka normalized audit`() {
        withTestApplication(
            moduleFunction = simpleEchoServer,
            test = {
                records.clear()

                val response = handleRequest(HttpMethod.Post, "/echo/audit") {
                    addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    addHeader("Job-Id", "1234")
                    setBody(""" { "id": 1337 } """)
                }.response

                Thread.sleep(250) // Audit streams are async to the request. We need to wait for them.

                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(2, records.size)

                val httpStream = KafkaHttpLogger.httpLogsStream
                val httpEvent =
                    records.find { it.topic() == httpStream.name } ?: throw AssertionError("http event missing")
                val deserializedHttpEvent = deserialize(httpStream.valueSerde, httpEvent)

                val auditStream = TestDescriptions.auditStream
                val auditEvent =
                    records.find { it.topic() == auditStream.name } ?: throw AssertionError("audit missing")
                val deserializedAuditEvent = deserializeAudit(auditEvent, TestDescriptions.echoWithAudit)

                assertEquals(TestDescriptions.echoWithAudit.fullName, deserializedHttpEvent.requestName)
                assertEquals("POST", deserializedAuditEvent.http.httpMethod.toUpperCase())
                assertEquals(SomeAudit(AUDIT_STRING), deserializedAuditEvent.request)
            }
        )
    }

    @Test
    fun `test audit deserialization of multiple messages`() {
        // TODO Should probably move this to another file. But just one test (for now)
        withTestApplication(
            moduleFunction = simpleEchoServer,
            test = {
                records.clear()

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
                val auditRecords = records.filter { it.topic() == TestDescriptions.auditStream.name }
                assertEquals(2, auditRecords.size)


                // We track that both records have been hit
                var echoWithAuditRecorded = false
                var echoWithoutAuditRecorded = false

                // Run the parser, like an ordinary consumer would
                for (record in auditRecords) {
                    val tree = defaultMapper.readTree(record.value())

                    TestDescriptions.echoWithAudit.parseAuditMessageOrNull(tree)?.let {
                        assertEquals(TestDescriptions.echoWithAudit.fullName, it.http.requestName)
                        assertEquals(AUDIT_STRING, it.request.audit)
                        echoWithAuditRecorded = true
                    }

                    TestDescriptions.echoId.parseAuditMessageOrNull(tree)?.let {
                        assertEquals(TestDescriptions.echoId.fullName, it.http.requestName)
                        assertEquals(requestId, it.request.id)
                        echoWithoutAuditRecorded = true
                    }
                }

                assertTrue(echoWithAuditRecorded)
                assertTrue(echoWithoutAuditRecorded)
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