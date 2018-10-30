package dk.sdu.cloud.service

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.client.RESTCallDescription
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.service.JsonSerde.jsonSerdeFromJavaType
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.application
import io.ktor.application.call
import io.ktor.application.featureOrNull
import io.ktor.features.origin
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.pipeline.PipelineContext
import io.ktor.request.ApplicationRequest
import io.ktor.request.contentType
import io.ktor.request.header
import io.ktor.request.httpMethod
import io.ktor.request.uri
import io.ktor.request.userAgent
import io.ktor.response.ApplicationSendPipeline
import io.ktor.util.AttributeKey
import kotlinx.coroutines.experimental.async
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.slf4j.LoggerFactory
import java.util.Collections

@Deprecated(
    "Replaced with SecurityPrincipalToken",
    replaceWith = ReplaceWith("SecurityPrincipalToken", "dk.sdu.cloud.SecurityPrincipalToken")
)
@Suppress("unused")
typealias LogEntryPrincipal = SecurityPrincipalToken

// Added by the server
data class HttpCallLogEntry(
    val jobId: String,
    val handledBy: ServiceInstance,
    val causedBy: String?,

    val requestName: String,
    val httpMethod: String,
    val uri: String,
    val userAgent: String?,
    val remoteOrigin: String,

    val token: SecurityPrincipalToken?,
    val requestContentType: String?,
    val requestSize: Long,
    val requestJson: Any?,

    val responseCode: Int,
    val responseTime: Long,
    val responseContentType: String,
    val responseSize: Long,
    val responseJson: Any?
)

class KafkaHttpRouteLogger {
    lateinit var description: RESTCallDescription<*, *, *, Any>
    private lateinit var serviceDescription: ServiceInstance
    private lateinit var kafka: KafkaServices
    private lateinit var httpProducer: EventProducer<String, HttpCallLogEntry>
    private lateinit var auditProducer: MappedEventProducer<String, AuditEvent<*>>

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
            httpProducer = kafka.producer.forStream(KafkaHttpLogger.httpLogsStream)

            if (!::description.isInitialized) {
                throw IllegalStateException(
                    "REST call description has not been initialized for KafkaHttpRouteLogger"
                )
            }

            @Suppress("UNCHECKED_CAST")
            auditProducer =
                    kafka.producer.forStream(description.auditStreamProducersOnly)
                            as MappedEventProducer<String, AuditEvent<*>>
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
        val requestPayload = call.attributes.getOrNull(requestPayloadToLogKey) ?: return@with run {
            log.warn("No request payload")
        }
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
            val principal = bearerToken?.let { TokenValidation.validateOrNull(it)?.toSecurityToken() }

            val entry = HttpCallLogEntry(
                jobId,
                serviceDescription,
                causedBy,
                description.fullName,
                method,
                uri,
                userAgent,
                remoteOrigin,
                principal,
                requestContentType,
                requestContentLength,
                requestPayload,
                statusCode,
                responseTime,
                responseContentType,
                responseSize,
                responsePayload
            )

            httpProducer.emit(jobId, entry)
            auditProducer.emit(AuditEvent(entry, requestPayload))
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, KafkaHttpRouteLogger, KafkaHttpRouteLogger> {
        private val log = LoggerFactory.getLogger(KafkaHttpRouteLogger::class.java)
        private val requestStartTime = AttributeKey<Long>("request-start-time")
        internal val requestPayloadToLogKey = AttributeKey<Any>("request-payload")
        internal val responsePayloadToLogKey = AttributeKey<Any>("response-payload")

        override val key: AttributeKey<KafkaHttpRouteLogger> = AttributeKey("kafka-http-route-log")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: KafkaHttpRouteLogger.() -> Unit
        ): KafkaHttpRouteLogger {
            val feature = KafkaHttpRouteLogger()
            feature.configure()

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
    val http: HttpCallLogEntry,
    val request: A
)

/**
 * Audit stream with correct serdes for a single [RESTCallDescription]
 *
 * __Strictly__ for producers. The topic is shared with all other [RESTCallDescription]s in that namespace, as a result
 * slightly more care must be taken when de-serializing the stream.
 */
private val <A : Any> RESTCallDescription<*, *, *, A>.auditStreamProducersOnly:
        MappedStreamDescription<String, AuditEvent<A>>
    get() = MappedStreamDescription(
        name = "audit.$namespace",
        keySerde = defaultSerdeOrJson(),
        valueSerde = auditSerde,
        mapper = { it.http.jobId }
    )

private val auditSerdeCache: MutableMap<String, Serde<AuditEvent<*>>> =
    Collections.synchronizedMap(HashMap<String, Serde<AuditEvent<*>>>())

private val auditJavaTypeCache: MutableMap<String, JavaType> = Collections.synchronizedMap(HashMap<String, JavaType>())

@Suppress("UNCHECKED_CAST")
private val <A : Any> RESTCallDescription<*, *, *, A>.auditSerde: Serde<AuditEvent<A>>
    get() {
        val fullName = fullName
        val cached = auditSerdeCache[fullName]
        if (cached != null) return cached as Serde<AuditEvent<A>>

        val newSerde = jsonSerdeFromJavaType<AuditEvent<A>>(auditJavaType)
        auditSerdeCache[fullName] = newSerde as Serde<AuditEvent<*>>
        return newSerde
    }

private val <A : Any> RESTCallDescription<*, *, *, A>.auditJavaType: JavaType
    get () {
        val fullName = fullName
        val cached = auditJavaTypeCache[fullName]
        if (cached != null) return cached

        val newJavaType = defaultMapper.typeFactory.constructParametricType(
            AuditEvent::class.java,
            defaultMapper.typeFactory.constructType(normalizedRequestTypeForAudit)
        )

        auditJavaTypeCache[fullName] = newJavaType
        return newJavaType
    }

val RESTDescriptions.auditStream: StreamDescription<String, String>
    get() = SimpleStreamDescription("audit.$namespace", Serdes.String(), Serdes.String())

private val requestNamePointer = JsonPointer.compile(
    JsonPointer.SEPARATOR +
            AuditEvent<*>::http.name +
            JsonPointer.SEPARATOR +
            HttpCallLogEntry::requestName.name
)

fun <A : Any> RESTCallDescription<*, *, *, A>.parseAuditMessageOrNull(tree: JsonNode): AuditEvent<A>? {
    val incomingRequestName =
        tree.at(requestNamePointer)?.takeIf { !it.isMissingNode && it.isTextual }?.textValue() ?: run {
            auditLog.warn("Could not find requestName field in message.")
            auditLog.warn("Message was: $tree")
            return null
        }

    if (incomingRequestName == fullName) {
        val type = auditJavaType
        val reader = defaultMapper.readerFor(type)
        return reader.readValue<AuditEvent<A>>(tree)
    }

    return null
}

private val auditLog = LoggerFactory.getLogger("dk.sdu.cloud.service.Audit")
