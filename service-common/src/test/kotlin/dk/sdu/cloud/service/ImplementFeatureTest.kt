package dk.sdu.cloud.service

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByIntId
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.client.bindEntireRequestFromBody
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

                val auditStream = TestDescriptions.echoId.auditStream
                val auditEvent =
                    records.find { it.topic() == auditStream.name } ?: throw AssertionError("audit missing")
                val deserializedAuditEvent = deserialize(auditStream.valueSerde, auditEvent)


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

                val auditStream = TestDescriptions.echoWithAudit.auditStream
                val auditEvent =
                    records.find { it.topic() == auditStream.name } ?: throw AssertionError("audit missing")
                val deserializedAuditEvent = deserialize(auditStream.valueSerde, auditEvent)

                assertEquals(TestDescriptions.echoWithAudit.fullName, deserializedHttpEvent.requestName)
                assertEquals("POST", deserializedAuditEvent.http.httpMethod.toUpperCase())
                assertEquals(SomeAudit(AUDIT_STRING), deserializedAuditEvent.request)
            }
        )
    }

    private fun <T> deserialize(serde: Serde<T>, record: ProducerRecord<String, String>): T {
        return serde.deserializer().deserialize(record.topic(), record.value().toByteArray())
    }

}