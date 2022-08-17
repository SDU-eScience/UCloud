package dk.sdu.cloud.service.test

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.JobId
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.server.application.*
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.setBody
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

data class KtorApplicationTestSetupContext(
    val application: Application,
    val micro: Micro
)

data class KtorApplicationTestContext(
    val engine: TestApplicationEngine,
    val micro: Micro
)

private fun TestApplicationRequest.setToken(token: String) {
    addHeader("Authorization", "Bearer $token")
}

fun TestApplicationRequest.setJobSubmitid(jobId: String = "jobId") {
    addHeader("JobSubmit-Id", Base64.getEncoder().encodeToString(jobId.toByteArray()))
}

fun TestApplicationRequest.setJobSubmitParam(parameter: String = "Parameter") {
    addHeader("JobSubmit-Parameter", Base64.getEncoder().encodeToString(parameter.toByteArray()))
}

fun KtorApplicationTestContext.sendRequest(
    method: HttpMethod,
    path: String,
    user: SecurityPrincipal?,
    params: Map<String, Any> = emptyMap(),
    configure: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    with(engine) {
        return handleRequest {
            this.method = method

            val uriParams =
                params.map { "${it.key}=${it.value.toString().encodeURLParameter()}" }.joinToString("&").let {
                    if (it.isNotEmpty()) "?$it" else ""
                }
            this.uri = path + uriParams

            if (user != null) {
                val token = TokenValidationMock.createTokenForPrincipal(user)
                setToken(token)
            }

            addHeader(HttpHeaders.JobId, UUID.randomUUID().toString())

            configure()
        }
    }
}

inline fun <reified RequestType : Any> KtorApplicationTestContext.sendJson(
    method: HttpMethod,
    path: String,
    request: RequestType,
    user: SecurityPrincipal?,
    params: Map<String, Any> = emptyMap(),
    crossinline configure: TestApplicationRequest.() -> Unit = {}
): TestApplicationCall {
    return sendRequest(method, path, user, params) {
        setBody(defaultMapper.encodeToString(request))
        configure()
    }
}

inline fun <reified RequestType : Any> TestApplicationCall.parseSuccessful(): RequestType {
    assertSuccess()

    if (RequestType::class.java == Unit::class.java) return Unit as RequestType

    val content = this.response.content
    assertNotNull(content)

    return defaultMapper.decodeFromString(content)
}

fun TestApplicationCall.assertHandled() {
    assertNotNull(this.response)
}

fun TestApplicationCall.assertStatus(httpStatusCode: HttpStatusCode) {
    assertHandled()

    val status = this.response.status()
    assertNotNull(status)
    assertEquals(httpStatusCode, status)
}

fun TestApplicationCall.assertSuccess() {
    assertHandled()

    val status = this.response.status()
    assertNotNull(status)
    if (!status.isSuccess()) {
        throw RPCException.fromStatusCode(dk.sdu.cloud.calls.HttpStatusCode(status.value, status.description))
    }
}

fun TestApplicationCall.assertFailure() {
    assertHandled()

    val status = this.response.status()
    assertNotNull(status)
    if (status.isSuccess()) {
        throw AssertionError("Expected response to be a failure, but instead was: ${response.status()}")
    }
}
