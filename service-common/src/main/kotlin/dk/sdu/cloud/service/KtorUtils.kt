package dk.sdu.cloud.service

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.client.AuthenticatedCloud
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
import io.ktor.server.engine.ApplicationEngine
import io.ktor.util.AttributeKey
import io.ktor.util.toMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dk.sdu.cloud.service.KtorUtilsKt")

// TODO Some of these should probably be a feature

fun Application.installDefaultFeatures(
    cloud: AuthenticatedCloud,
    kafkaServices: KafkaServices,
    requireJobId: Boolean = true
) {
    log.info("Installing default features. requireJobId=$requireJobId")

    install(CallLogging)
    install(DefaultHeaders)
    install(KafkaHttpLogger) {
        kafka = kafkaServices
    }

    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
        }
    }

    install(CloudClient) {
        baseCloud = cloud
    }

    intercept(ApplicationCallPipeline.Infrastructure) {
        val jobId = call.request.headers["Job-Id"]
        if (jobId == null) {
            if (requireJobId) {
                log.debug("Did not receive a valid Job-Id in the header of the request!")
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

    log.info("$method $uri $jobId $causedBy $parameterString $headerString")
}

fun <R : Any> RESTHandler<R, *, *>.logEntry(
    log: Logger,
    payload: R,
    requestToString: (R) -> String = { it.toString() }
) {
    val method = call.request.httpMethod.value
    val uri = call.request.uri
    val jobId = call.request.safeJobId
    val causedBy = call.request.causedBy


    log.info("$method $uri $jobId $causedBy ${requestToString(payload)}")
}

typealias HttpServerProvider = (Application.() -> Unit) -> ApplicationEngine
