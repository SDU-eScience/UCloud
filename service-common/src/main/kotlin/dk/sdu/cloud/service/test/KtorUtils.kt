package dk.sdu.cloud.service.test

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
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

fun Application.configureBaseServer(micro: Micro, vararg controllers: Controller) {
    installDefaultFeatures(micro)
    routing {
        configureControllers(*controllers)
    }
}

private fun TestApplicationRequest.setToken(token: String) {
    addHeader("Authorization", "Bearer $token")
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

            addHeader("Job-Id", UUID.randomUUID().toString())

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
        setBody(defaultMapper.writeValueAsString(request))
        configure()
    }
}

inline fun <reified RequestType : Any> TestApplicationCall.parseSuccessful(): RequestType {
    assertSuccess()

    if (RequestType::class.java == Unit::class.java) return Unit as RequestType

    val content = this.response.content
    assertNotNull(content)

    return defaultMapper.readValue(content)
}

fun TestApplicationCall.assertHandled() {
    assert(requestHandled)
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
        throw AssertionError("Expected response to be successful, but instead was: ${response.status()}")
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

fun withKtorTest(
    setup: KtorApplicationTestSetupContext.() -> List<Controller>,
    test: KtorApplicationTestContext.() -> Unit,

    microArgs: List<String> = emptyList(),
    microConfigure: Micro.() -> Unit = {}
) {
    val micro = initializeMicro(microArgs)
    micro.microConfigure()

    withTestApplication(
        moduleFunction = {
            val controllers = KtorApplicationTestSetupContext(this, micro).setup()

            configureBaseServer(micro, *controllers.toTypedArray())
        },

        test = {
            KtorApplicationTestContext(this, micro).test()
        }
    )
}
