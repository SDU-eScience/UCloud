package dk.sdu.cloud.service

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.client.RESTCallDescription
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.content.OutgoingContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.PipelineContext
import io.ktor.request.ApplicationRequest
import io.ktor.request.contentType
import io.ktor.request.header
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
    JsonSubTypes.Type(value = HttpProxyCallLogEntry::class, name = "proxy"),
    JsonSubTypes.Type(value = HttpResponseCallLogEntry::class, name = "response"),
    JsonSubTypes.Type(value = HttpRequestCallLogEntry::class, name = "request")
)
sealed class HttpCallLogEntry {
    abstract val jobId: String
    //abstract val handledBy: LogEntryServerDescription
}

// Added by the gateway. If we see a proxy entry but no request entry we know that something went wrong between them
data class HttpProxyCallLogEntry(
    override val jobId: String,
    //override val handledBy: LogEntryServerDescription,

    val userAgent: String,
    val origin: String
) : HttpCallLogEntry()

// Hide sensitive information (i.e. the signature) and keep just the crucial information
// We can still infer which JWT was used (from sub, iat, and exp should limit the number of JWTs to one). From this
// we can determine which refreshToken was used to generate it.
data class LogEntryPrincipal(
    val id: String,
    val role: String,

    val tokenIssuedAt: Long,
    val tokenExpiresAt: Long
)

data class LogEntryServerDescription(
    val hostname: String,
    val serviceName: String,
    val version: String,
    val port: Int
)

// Added by the server
data class HttpRequestCallLogEntry(
    override val jobId: String,
    //override val handledBy: LogEntryServerDescription,

    val principal: LogEntryPrincipal?,
    val requestContentType: String?,
    val requestSize: Long
) : HttpCallLogEntry()

// Added by the server
data class HttpResponseCallLogEntry(
    override val jobId: String,
    //override val handledBy: LogEntryServerDescription,

    val responseCode: Int,
    val responseTime: Long,
    val responseContentType: String,
    val responseSize: Long
) : HttpCallLogEntry()


// TODO(Dan): The amount of data we send through these are going to be rather large.
// This will likely become a problem. However, as we have discussed this is likely to be very useful during early
// phases development. Thus, for now, we will just emit the data and think about dealing with the large amount of data
// later.

class KafkaHttpRouteLogger {
    //lateinit var serverDescription: LogEntryServerDescription
    lateinit var producer: EventProducer<String, HttpCallLogEntry>

    private val ApplicationRequest.bearer: String?
        get() {
            val header = header(HttpHeaders.Authorization) ?: return null
            if (!header.startsWith("Bearer ")) {
                return null
            }
            return header.substringAfter("Bearer ")
        }

    private suspend fun interceptBefore(context: PipelineContext<Unit, ApplicationCall>) = with(context) {
        val jobId = call.request.safeJobId
        val bearerToken = call.request.bearer
        val contentType = call.request.contentType().toString()
        val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0

        if (jobId != null) {
            call.attributes.put(requestStartTime, System.currentTimeMillis())

            async {
                // TODO This will cause more decoding than is needed. Need to use the computation from auth-api
                val decodedToken = bearerToken?.let { TokenValidation.validateOrNull(it) }
                val principal = decodedToken?.let {
                    LogEntryPrincipal(
                        it.subject,
                        it.getClaim("role").asString(),
                        it.issuedAt.time,
                        it.expiresAt.time
                    )
                }

                val entry = HttpRequestCallLogEntry(jobId, principal, contentType, contentLength)
                producer.emit(jobId, entry)
            }
        }
    }

    private suspend fun interceptAfter(
        context: PipelineContext<Any, ApplicationCall>,
        message: Any
    ) = with(context) {
        val jobId = call.request.safeJobId ?: return@with run {
            log.debug("Missing jobId")
        }

        val startTime = call.attributes.getOrNull(requestStartTime) ?: return@with run {
            log.debug("Missing start time")
        }
        val responseTime = System.currentTimeMillis() - startTime

        val statusCode = when (message) {
            is HttpStatusCode -> message.value
            is OutgoingContent -> message.status?.value
            else -> null
        } ?: context.context.response.status()?.value ?: return@with run {
            log.debug("Missing statusCode: $message")
        }


        val contentType = when (message) {
            is OutgoingContent -> message.headers[HttpHeaders.ContentType]
            else -> null
        } ?: ContentType.Any.toString()

        val responseSize = when (message) {
            is OutgoingContent -> message.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            else -> 0
        } ?: 0


        async {
            val entry = HttpResponseCallLogEntry(jobId, statusCode, responseTime, contentType, responseSize)
            producer.emit(jobId, entry)
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, KafkaHttpRouteLogger, KafkaHttpRouteLogger> {
        private val log = LoggerFactory.getLogger(KafkaHttpRouteLogger::class.java)
        private val requestStartTime = AttributeKey<Long>("request-start-time")
        override val key: AttributeKey<KafkaHttpRouteLogger> = AttributeKey("kafka-http-route-log")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: KafkaHttpRouteLogger.() -> Unit
        ): KafkaHttpRouteLogger {
            val feature = KafkaHttpRouteLogger()
            feature.configure()

            if (!feature::producer.isInitialized) {
                throw IllegalStateException("producer has not been initialized")
            }

            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.interceptBefore(this) }
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) { feature.interceptAfter(this, it) }
            return feature
        }
    }
}

class KafkaHttpLogger {
    lateinit var kafka: KafkaServices

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, KafkaHttpLogger, KafkaHttpLogger> {
        override val key: AttributeKey<KafkaHttpLogger> = AttributeKey("kafka-http-logger")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: KafkaHttpLogger.() -> Unit
        ): KafkaHttpLogger {
            val feature = KafkaHttpLogger()
            feature.configure()
            if (!feature::kafka.isInitialized) {
                throw IllegalStateException("kafka has not been initialized")
            }
            return feature
        }
    }
}

fun RESTCallDescription<*, *, *>.loggingStream(): StreamDescription<String, HttpCallLogEntry>? {
    val capturedFullName = fullName
    return if (capturedFullName == null) null
    else SimpleStreamDescription(capturedFullName, defaultSerdeOrJson(), defaultSerdeOrJson())
}
