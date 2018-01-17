package dk.sdu.cloud.service

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.*
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.pipeline.PipelineContext
import io.ktor.request.ApplicationRequest
import io.ktor.request.httpMethod
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.util.AttributeKey
import io.ktor.util.toMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dk.sdu.cloud.service.KtorUtils")

// TODO Some of these should probably be a feature

fun Application.installDefaultFeatures(requireJobId: Boolean = true) {
    install(CallLogging)
    install(DefaultHeaders)
    install(ContentNegotiation) {
        jackson { registerKotlinModule() }
    }

    intercept(ApplicationCallPipeline.Infrastructure) {
        val uuid = call.request.headers["Job-Id"] ?: run {
            if (requireJobId) {
                log.debug("Did not receive a valid Job-Id in the header of the request!")
                call.respond(HttpStatusCode.BadRequest)
                finish()
            }
            return@intercept
        }
        call.request.jobId = uuid
    }
}

private val jobIdKey = AttributeKey<String>("job-id")
var ApplicationRequest.jobId: String
    get() = call.attributes[jobIdKey]
    private set(value) = call.attributes.put(jobIdKey, value)

val ApplicationRequest.safeJobId: String?
    get() = call.attributes.getOrNull(jobIdKey)

fun PipelineContext<*, ApplicationCall>.logEntry(
        log: Logger,
        additionalParameters: Map<String, Any?>? = null,
        parameterIncludeFilter: ((String) -> Boolean)? = null,
        headerIncludeFilter: ((String) -> Boolean)? = null
) {
    val method = call.request.httpMethod.value
    val uri = call.request.uri
    val jobId = call.request.safeJobId

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

    log.info("$method $uri $jobId $parameterString $headerString")
}

fun <R : Any> RESTHandler<R, *, *>.logEntry(
        log: Logger,
        payload: R,
        requestToString: (R) -> String = { it.toString() }
) {
    val method = call.request.httpMethod.value
    val uri = call.request.uri
    val jobId = call.request.safeJobId


    log.info("$method $uri $jobId ${requestToString(payload)}")
}
