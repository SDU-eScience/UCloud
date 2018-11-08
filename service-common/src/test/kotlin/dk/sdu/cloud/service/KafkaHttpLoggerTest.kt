package dk.sdu.cloud.service

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.service.test.KafkaMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.messagesForTopic
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import kotlin.test.Test
import java.lang.RuntimeException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LoggingRequest(val foo: String)

object LoggingDescriptions : RESTDescriptions("logging") {
    val baseContext = "/logging"

    val internalError = callDescription<LoggingRequest, Unit, CommonErrorMessage> {
        name = "internalError"
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"internal-error"
        }

        auth {
            access = AccessRight.READ
        }

        body { bindEntireRequestFromBody() }
    }
}

class LoggingController : Controller {
    override val baseContext: String = LoggingDescriptions.baseContext
    override fun configure(routing: Route): Unit = with(routing) {
        implement(LoggingDescriptions.internalError) {
            throw RuntimeException("This is an internal error")
        }
    }
}

class KafkaHttpLoggerTest {
    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        listOf(LoggingController())
    }

    @Test
    fun `test internal errors are logged`() {
        withKtorTest(
            setup = setup,
            test = {
                val request = LoggingRequest("foo42")

                val resp = sendJson(HttpMethod.Post, "/logging/internal-error", request, TestUsers.user)
                resp.assertStatus(HttpStatusCode.InternalServerError)

                val messages = KafkaMock.messagesForTopic(LoggingDescriptions.auditStream)
                assertThatPropertyEquals(messages, { it.size }, 1)

                val messageAsJson = defaultMapper.readTree(messages.single().second)
                val auditMessage = LoggingDescriptions.internalError.parseAuditMessageOrNull(messageAsJson)
                assertNotNull(auditMessage)

                assertEquals(request, auditMessage.request)
                assertEquals(500, auditMessage.http.responseCode)

                val token = auditMessage.http.token
                assertNotNull(token)
                assertThatPropertyEquals(token, { it.principal }, TestUsers.user)
            }
        )
    }
}
