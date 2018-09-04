package dk.sdu.cloud.service

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.client.RESTCallDescription
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.service.JsonSerde.jsonSerdeFromJavaType
import io.ktor.application.*
import io.ktor.features.origin
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.pipeline.PipelineContext
import io.ktor.request.*
import io.ktor.response.ApplicationSendPipeline
import io.ktor.util.AttributeKey
import kotlinx.coroutines.experimental.async
import org.slf4j.LoggerFactory

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = HttpRequestHandledEntry::class, name = "request")
)
sealed class HttpCallLogEntry {
    abstract val jobId: String
    abstract val handledBy: ServiceInstance
}

// Hide sensitive information (i.e. the signature) and keep just the crucial information
// We can still infer which JWT was used (from sub, iat, and exp should limit the number of JWTs to one). From this
// we can determine which refreshToken was used to generate it.
data class LogEntryPrincipal(
    val id: String,
    val role: String,

    val tokenIssuedAt: Long,
    val tokenExpiresAt: Long
)

// Added by the server
data class HttpRequestHandledEntry(
    override val jobId: String,
    override val handledBy: ServiceInstance,
    val causedBy: String?,

    val requestName: String,
    val httpMethod: String,
    val uri: String,
    val userAgent: String?,
    val remoteOrigin: String,

    val principal: LogEntryPrincipal?,
    val requestContentType: String?,
    val requestSize: Long,
    val requestJson: Any?,

    val responseCode: Int,
    val responseTime: Long,
    val responseContentType: String,
    val responseSize: Long,
    val responseJson: Any?
) : HttpCallLogEntry()

class KafkaHttpRouteLogger {
    lateinit var requestName: String
    private lateinit var serviceDescription: ServiceInstance
    private lateinit var kafka: KafkaServices
    private lateinit var producer: EventProducer<String, HttpCallLogEntry>

    private val ApplicationRequest.bearer: String?
        get() {
            val header = header(HttpHeaders.Authorization) ?: return null
            if (!header.startsWith("Bearer ")) {
                return null
            }
            return header.substringAfter("Bearer ")
        }

    private fun PipelineContext<*, ApplicationCall>.loadFromParentFeature() {
        if (!::serviceDescription.isInitialized) {
            val feature = application.featureOrNull(KafkaHttpLogger)
                    ?: throw IllegalStateException("Could not find the KafkaHttpLogger feature on the application")
            serviceDescription = feature.serverDescription
            kafka = feature.kafka
            producer = kafka.producer.forStream(KafkaHttpLogger.httpLogsStream)
        }
    }

    private suspend fun interceptBefore(context: PipelineContext<Unit, ApplicationCall>) = with(context) {
        loadFromParentFeature()
        val jobId = call.request.safeJobId
        if (jobId != null) {
            call.attributes.put(requestStartTime, System.currentTimeMillis())
        }
    }

    private suspend fun interceptAfter(
        context: PipelineContext<Any, ApplicationCall>,
        message: Any
    ) = with(context) {
        loadFromParentFeature()

        val bearerToken = call.request.bearer
        val requestContentType = call.request.contentType().toString()
        val requestContentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0
        val method = call.request.httpMethod.value
        val uri = call.request.uri
        val requestPayload = call.attributes.getOrNull(requestPayloadToLogKey)
        val causedBy = call.request.causedBy
        val remoteOrigin = call.request.origin.remoteHost
        val userAgent = call.request.userAgent()

        val jobId = call.request.safeJobId ?: return@with run {
            log.debug("Missing jobId")
        }

        val startTime = call.attributes.getOrNull(requestStartTime) ?: return@with run {
            log.warn("Missing start time. This should probably not happen.")
        }
        val responseTime = System.currentTimeMillis() - startTime

        val statusCode = when (message) {
            is HttpStatusCode -> message.value
            is OutgoingContent -> message.status?.value
            else -> null
        } ?: context.context.response.status()?.value ?: return@with run {
            log.debug("Missing statusCode: $message")
        }

        val responseContentType = when (message) {
            is OutgoingContent -> message.headers[HttpHeaders.ContentType]
            else -> null
        } ?: ContentType.Any.toString()

        val responseSize = when (message) {
            is OutgoingContent -> message.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            else -> 0
        } ?: 0

        val responsePayload = call.attributes.getOrNull(responsePayloadToLogKey)

        async {
            val token = bearerToken?.let { TokenValidation.validateOrNull(it) }
            val principal = if (token != null) {
                LogEntryPrincipal(
                    token.subject, token.getClaim("role").asString(),
                    token.issuedAt.time, token.expiresAt.time
                )
            } else {
                null
            }

            val entry = HttpRequestHandledEntry(
                jobId, serviceDescription, causedBy, requestName, method, uri, userAgent, remoteOrigin, principal,
                requestContentType, requestContentLength, requestPayload, statusCode, responseTime,
                responseContentType, responseSize, responsePayload
            )

            producer.emit(jobId, entry)
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, KafkaHttpRouteLogger, KafkaHttpRouteLogger> {
        private val log = LoggerFactory.getLogger(KafkaHttpRouteLogger::class.java)
        private val requestStartTime = AttributeKey<Long>("request-start-time")
        val requestPayloadToLogKey = AttributeKey<Any>("request-payload")
        val responsePayloadToLogKey = AttributeKey<Any>("response-payload")

        override val key: AttributeKey<KafkaHttpRouteLogger> = AttributeKey("kafka-http-route-log")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: KafkaHttpRouteLogger.() -> Unit
        ): KafkaHttpRouteLogger {
            val feature = KafkaHttpRouteLogger()
            feature.configure()
            feature.requestName

            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.interceptBefore(this) }
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) { feature.interceptAfter(this, it) }
            return feature
        }
    }
}

class KafkaHttpLogger {
    lateinit var kafka: KafkaServices
    lateinit var serverDescription: ServiceInstance

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, KafkaHttpLogger, KafkaHttpLogger> {
        override val key: AttributeKey<KafkaHttpLogger> = AttributeKey("kafka-http-logger")
        val httpLogsStream = SimpleStreamDescription<String, HttpCallLogEntry>(
            "http.logs", defaultSerdeOrJson(),
            defaultSerdeOrJson()
        )

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: KafkaHttpLogger.() -> Unit
        ): KafkaHttpLogger {
            val feature = KafkaHttpLogger()
            feature.configure()
            if (!feature::kafka.isInitialized) {
                throw IllegalStateException("kafka has not been initialized")
            }
            if (!feature::serverDescription.isInitialized) {
                throw IllegalStateException("serverDescription has not been initialized")
            }
            return feature
        }
    }
}

data class AuditEvent<A>(
    val http: HttpRequestHandledEntry,
    val request: A
)

val <A : Any> RESTCallDescription<*, *, *, A>.auditStream: MappedStreamDescription<String, AuditEvent<A>>
    get() = MappedStreamDescription(
        name = "audit.$fullName",
        keySerde = defaultSerdeOrJson(),
        valueSerde = jsonSerdeFromJavaType(
            defaultMapper.typeFactory.constructParametricType(
                AuditEvent::class.java,
                defaultMapper.typeFactory.constructType(normalizedRequestTypeForAudit)
            )
        ),
        mapper = { it.http.jobId }
    )
