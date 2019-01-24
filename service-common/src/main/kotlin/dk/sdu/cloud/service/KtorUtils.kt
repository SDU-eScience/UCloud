package dk.sdu.cloud.service

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.features.callIdMdc
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.Routing
import io.ktor.routing.route
import io.ktor.server.engine.ApplicationEngine
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.toMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val utilsLog = LoggerFactory.getLogger("dk.sdu.cloud.service.KtorUtilsKt")
internal val healthLog = LoggerFactory.getLogger("dk.sdu.cloud.service.HealthCheck")

fun Application.installDefaultFeatures(micro: Micro) {
    // Default ktor features


    install(DefaultHeaders)
    install(XForwardedHeaderSupport)
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()

            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
            configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
        }
    }

    // Custom features
    install(KtorMicroServiceFeature) {
        this.micro = micro
    }

    install(KafkaHttpLogger) {
        kafka = micro.kafka
        serverDescription = micro.serviceInstance
    }

    install(CloudClient) {
        baseCloud = micro.authenticatedCloud
    }

    // Basic interceptors
    interceptJobId(requireJobId = !micro.developmentModeEnabled)
    interceptHealthCheck()

    install(CallLogging) {
        mdc("request-id") { call ->
            call.request.header("Job-Id")
        }
    }
}

private fun Application.interceptHealthCheck() {
    intercept(ApplicationCallPipeline.Features) {
        if (call.request.path() == HEALTH_URI) {
            healthLog.debug("Received request for health!")
            call.respond(HttpStatusCode.NoContent)
            finish()
            return@intercept
        }
    }
}

fun Application.interceptJobId(requireJobId: Boolean) {
    intercept(ApplicationCallPipeline.Features) {
        val jobId = call.request.headers["Job-Id"]
        if (jobId == null) {
            if (requireJobId) {
                utilsLog.debug("Did not receive a valid Job-Id in the header of the request!")
                call.respond(HttpStatusCode.BadRequest)
                finish()
                return@intercept
            }
        } else {
            call.request.jobId = jobId
        }

        val causedBy = call.request.headers["Caused-By"]
        if (causedBy != null) call.request.causedBy = causedBy
    }
}

const val HEALTH_URI = "/health"

private val jobIdKey = AttributeKey<String>("job-id")
var ApplicationRequest.jobId: String
    get() = call.attributes[jobIdKey]
    private set(value) = call.attributes.put(jobIdKey, value)

val ApplicationRequest.safeJobId: String?
    get() = call.attributes.getOrNull(jobIdKey)

private val causedByKey = AttributeKey<String>("caused-by")
var ApplicationRequest.causedBy: String?
    get() = call.attributes.getOrNull(causedByKey)
    private set(value) {
        if (value == null) {
            call.attributes.remove(causedByKey)
        } else {
            call.attributes.put(causedByKey, value)
        }
    }

fun PipelineContext<*, ApplicationCall>.logEntry(
    log: Logger,
    additionalParameters: Map<String, Any?>? = null,
    parameterIncludeFilter: ((String) -> Boolean)? = null,
    headerIncludeFilter: ((String) -> Boolean)? = null
) {
    val method = call.request.httpMethod.value
    val uri = call.request.uri
    val jobId = call.request.safeJobId
    val causedBy = call.request.causedBy

    val callParameters =
        if (parameterIncludeFilter == null) null
        else call.request.queryParameters.toMap()
            .filterKeys { parameterIncludeFilter(it) }

    val allParameters = HashMap<String, Any?>().apply {
        if (callParameters != null) putAll(callParameters)
        if (additionalParameters != null) putAll(additionalParameters)
    }

    val parameterString = if (allParameters.isEmpty()) {
        ""
    } else {
        val parameters = allParameters
            .map { "${it.key} -> ${it.value}" }
            .joinToString(", ")
        "[PARAMETERS: $parameters]"
    }

    val headerString = if (headerIncludeFilter == null) {
        ""
    } else {
        val justHeaders = call.request.headers.toMap()
            .filterKeys { headerIncludeFilter(it) }
            .map { "${it.key} -> ${it.value}" }
            .joinToString(", ")

        "[HEADERS: $justHeaders]"
    }

    log.info("$method $uri jobId=$jobId causedBy=$causedBy payload={$parameterString $headerString}")
}

@Deprecated(message = "Included by default. Override toString if needed on request type")
fun <R : Any> RESTHandler<R, *, *, *>.logEntry(
    log: Logger,
    payload: R,
    requestToString: (R) -> String = { it.toString() }
) {
    // Do nothing
}

typealias HttpServerProvider = (Application.() -> Unit) -> ApplicationEngine

interface Controller {
    val baseContext: String
    fun configure(routing: Route)
}

fun Routing.configureControllers(vararg controllers: Controller) {
    controllers.forEach {
        route(it.baseContext) {
            it.configure(this)
        }
    }
}
