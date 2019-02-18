package dk.sdu.cloud.service

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.auditStream
import dk.sdu.cloud.calls.server.parseAuditMessageOrNull
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.test.KafkaMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.messagesForTopic
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

data class LoggingRequest(val foo: String)
data class OneTimeTokenRequest(val token: String) {
    override fun toString(): String = "OneTimeTokenRequest()"
}

object LoggingDescriptions : CallDescriptionContainer("logging") {
    val baseContext = "/logging"

    val internalError = call<LoggingRequest, Unit, CommonErrorMessage>("internalError") {
        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
                +"internal-error"
            }

            body { bindEntireRequestFromBody() }
        }

        auth {
            access = AccessRight.READ
        }
    }

    val oneTimeTokenNotThroughHeader =
        call<OneTimeTokenRequest, Unit, CommonErrorMessage>("oneTimeTokenNotThroughHeader") {
            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"ott"
                }

                params {
                    +boundTo(OneTimeTokenRequest::token)
                }
            }

            auth {
                roles = Roles.PUBLIC
                access = AccessRight.READ
            }
        }
}

class LoggingController(private val tokenValidation: TokenValidation<Any>) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(LoggingDescriptions.internalError) {
            throw RuntimeException("This is an internal error")
        }

        implement(LoggingDescriptions.oneTimeTokenNotThroughHeader) {
            val token = tokenValidation.validateAndDecodeOrNull(request.token) ?: throw RPCException.fromStatusCode(
                HttpStatusCode.Forbidden
            )
            ctx.audit.securityPrincipalTokenToAudit = token
            ok(Unit)
        }
    }
}

class KafkaHttpLoggerTest {
    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        listOf(LoggingController(micro.tokenValidation))
    }

    @Test
    fun `test internal errors are logged`() {
        withKtorTest(
            setup = setup,
            test = {
                val request = LoggingRequest("foo42")

                sendJson(HttpMethod.Post, "/logging/internal-error", request, TestUsers.user)
                    .assertStatus(HttpStatusCode.InternalServerError)

                val messages = KafkaMock.messagesForTopic(LoggingDescriptions.auditStream)
                assertThatPropertyEquals(messages, { it.size }, 1)

                val messageAsJson = defaultMapper.readTree(messages.single().second)
                val auditMessage = LoggingDescriptions.internalError.parseAuditMessageOrNull<Any>(
                    messageAsJson,
                    acceptRequestsWithServerFailure = true
                )
                assertNotNull(auditMessage)

                assertEquals(request, auditMessage.request)
                assertEquals(500, auditMessage.http.responseCode)

                val token = auditMessage.http.token
                assertNotNull(token)
                assertThatPropertyEquals(token, { it.principal }, TestUsers.user)
            }
        )
    }

    @Test
    fun `test one-time token through param`() {
        // Note: NEVER EVER PUT NON ONE-TIME TOKENS IN A QUERY PARAM
        withKtorTest(
            setup = setup,
            test = {
                val user = TestUsers.user
                val token = TokenValidationMock.createTokenForPrincipal(user)
                val sendRequest = sendRequest(
                    HttpMethod.Get, "/logging/ott", user = null, params = mapOf(
                        "token" to token
                    )
                )
                sendRequest.assertSuccess()

                val messages = KafkaMock.messagesForTopic(LoggingDescriptions.auditStream)
                assertThatPropertyEquals(messages, { it.size }, 1)

                val messageAsJson = defaultMapper.readTree(messages.single().second)
                val auditMessage = LoggingDescriptions.oneTimeTokenNotThroughHeader
                    .parseAuditMessageOrNull<OneTimeTokenRequest>(messageAsJson)
                assertNotNull(auditMessage)

                assertEquals(token, auditMessage.request.token)

                val auditedToken = auditMessage.http.token
                assertNotNull(auditedToken)
                assertThatPropertyEquals(auditedToken, { it.principal }, user)
            }
        )
    }
}
