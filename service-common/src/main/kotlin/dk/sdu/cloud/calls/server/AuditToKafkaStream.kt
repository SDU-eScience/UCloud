package dk.sdu.cloud.calls.server

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import dk.sdu.cloud.calls.AuditDescription
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auditOrNull
import dk.sdu.cloud.calls.jvmClass
import dk.sdu.cloud.calls.kClass
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.kafka.EventProducer
import dk.sdu.cloud.kafka.JsonSerde.jsonSerdeFromJavaType
import dk.sdu.cloud.micro.KafkaServices
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.kafka.MappedEventProducer
import dk.sdu.cloud.kafka.MappedStreamDescription
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.kafka.SimpleStreamDescription
import dk.sdu.cloud.kafka.StreamDescription
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.kafka.defaultSerdeOrJson
import dk.sdu.cloud.kafka.forStream
import io.ktor.application.call
import io.ktor.features.origin
import io.ktor.http.HttpHeaders
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.request.userAgent
import io.ktor.util.date.toGMTDate
import kotlinx.coroutines.launch
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.time.ZonedDateTime
import java.util.*

private typealias HttpEventProducer = EventProducer<String, HttpCallLogEntry>
private typealias AuditProducer = MappedEventProducer<String, AuditEvent<*>>

class AuditToKafkaStream(
    private val instance: ServiceInstance,
    private val kafka: KafkaServices,
    private val tokenValidation: TokenValidation<Any>
) {
    private val eventProducerCache = HashMap<CallDescription<*, *, *>, HttpEventProducer>()
    private val auditProducerCache = HashMap<CallDescription<*, *, *>, AuditProducer>()

    private fun httpEventProducer(call: CallDescription<*, *, *>): HttpEventProducer {
        synchronized(this) {
            val cached = eventProducerCache[call]
            if (cached != null) return cached

            val eventProducer = kafka.producer.forStream(httpLogsStream)
            eventProducerCache[call] = eventProducer
            return eventProducer
        }
    }

    private fun auditProducer(call: CallDescription<*, *, *>): AuditProducer {
        synchronized(this) {
            val cached = auditProducerCache[call]
            if (cached != null) return cached

            val auditProducer = kafka.producer.forStream(call.auditStreamProducersOnly)
            auditProducerCache[call] = auditProducer
            return auditProducer
        }
    }

    private val ApplicationRequest.bearer: String?
        get() {
            val header = header(HttpHeaders.Authorization) ?: return null
            if (!header.startsWith("Bearer ")) {
                return null
            }
            return header.substringAfter("Bearer ")
        }

    fun register(server: RpcServer) {
        server.attachFilter(IngoingCallFilter.beforeParsing(HttpCall) { _ ->
            audit = AuditData(System.currentTimeMillis())
        })

        server.attachFilter(IngoingCallFilter.afterResponse(HttpCall) { callDescription, request, result ->
            val auditDescription = callDescription.auditOrNull
            val auditData = audit
            val eventProducer = httpEventProducer(callDescription)
            val auditProducer = auditProducer(callDescription)

            val bearerToken = call.request.bearer
            val requestContentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0
            val causedBy = call.request.header(HttpHeaders.CausedBy)
            val remoteOrigin = call.request.origin.remoteHost
            val userAgent = call.request.userAgent()

            val startTime = auditData.requestStart
            val responseTime = System.currentTimeMillis() - startTime

            val responseCode = result.statusCode.value

            val token = run {
                val principalFromBearerToken = bearerToken
                    ?.let { tokenValidation.validateOrNull(it) }
                    ?.let { tokenValidation.decodeToken(it) }

                val principalFromOverride = auditData.securityPrincipalTokenToAudit
                principalFromOverride ?: principalFromBearerToken
            }

            val auditPayload = auditData.requestToAudit ?: request

            if (auditDescription != null) {
                if (auditPayload == null) {
                    // We can, in this case, still produce a message. But only because the audit payload will be null.
                } else {
                    val expectedType = auditDescription.auditType.type.jvmClass
                    if (expectedType != auditPayload.javaClass) {
                        repeat(5) {
                            log.warn(
                                "Audit payload does not match the expected type. " +
                                        "We got ${auditPayload.javaClass} but expected $expectedType"
                            )
                            log.warn("No audit trace has been produced")
                            // We cannot create an audit track since it will mess up the resulting elastic
                            // index (bad type)
                            return@afterResponse
                        }
                    }
                }
            }

            val expiresPeriod = auditDescription?.retentionPeriod ?: AuditDescription.DEFAULT_RETENTION_PERIOD
            val expiry = ZonedDateTime.now().plus(expiresPeriod).toGMTDate().timestamp

            launch {
                val entry = HttpCallLogEntry(
                    jobId = jobId,
                    handledBy = instance,
                    causedBy = causedBy,
                    requestName = callDescription.fullName,
                    userAgent = userAgent,
                    remoteOrigin = remoteOrigin,
                    token = token,
                    requestSize = requestContentLength,
                    requestJson = auditPayload,
                    responseCode = responseCode,
                    responseTime = responseTime,
                    expiry = expiry
                )

                eventProducer.emit(jobId, entry)
                auditProducer.emit(AuditEvent(entry, auditPayload))
            }
        })
    }

    companion object : Loggable {
        override val log = logger()

        private val httpLogsStream = SimpleStreamDescription<String, HttpCallLogEntry>(
            "http.logs", defaultSerdeOrJson(),
            defaultSerdeOrJson()
        )
    }
}

/**
 * Audit stream with correct serdes for a single [CallDescription]
 *
 * __Strictly__ for producers. The topic is shared with all other [CallDescription]s in that namespace, as a result
 * slightly more care must be taken when de-serializing the stream.
 */
private val CallDescription<*, *, *>.auditStreamProducersOnly:
        MappedStreamDescription<String, AuditEvent<*>>
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
private val CallDescription<*, *, *>.auditSerde: Serde<AuditEvent<*>>
    get() {
        val fullName = fullName
        val cached = auditSerdeCache[fullName]
        if (cached != null) return cached

        val newSerde = jsonSerdeFromJavaType<AuditEvent<Any>>(auditJavaType)
        auditSerdeCache[fullName] = newSerde as Serde<AuditEvent<*>>
        return newSerde
    }

private val CallDescription<*, *, *>.auditJavaType: JavaType
    get () {
        val fullName = fullName
        val cached = auditJavaTypeCache[fullName]
        if (cached != null) return cached

        val newJavaType = defaultMapper.typeFactory.constructParametricType(
            AuditEvent::class.java,
            defaultMapper.typeFactory.constructType(
                auditOrNull?.auditType ?: requestType
            )
        )

        auditJavaTypeCache[fullName] = newJavaType
        return newJavaType
    }

val CallDescriptionContainer.auditStream: StreamDescription<String, String>
    get() = SimpleStreamDescription("audit.$namespace", Serdes.String(), Serdes.String())

private val requestNamePointer = JsonPointer.compile(
    JsonPointer.SEPARATOR +
            AuditEvent<*>::http.name +
            JsonPointer.SEPARATOR +
            HttpCallLogEntry::requestName.name
)

/**
 * Parses an audit message from [tree].
 *
 * If [acceptRequestsWithServerFailure] is true then all audit messages matching the description is accepted. Otherwise
 * only messages that do not have a status code of 5XX will be accepted.
 */
fun <A : Any> CallDescription<*, *, *>.parseAuditMessageOrNull(
    tree: JsonNode,
    acceptRequestsWithServerFailure: Boolean = false
): AuditEvent<A>? {
    val incomingRequestName =
        tree.at(requestNamePointer)?.takeIf { !it.isMissingNode && it.isTextual }?.textValue() ?: run {
            return null
        }

    if (incomingRequestName == fullName) {
        val type = auditJavaType
        val reader = defaultMapper.readerFor(type)
        @Suppress("UNCHECKED_CAST")
        return reader.readValue<AuditEvent<Any>>(tree)
            ?.takeIf { acceptRequestsWithServerFailure || it.http.responseCode !in 500..599 } as AuditEvent<A>?
    }

    return null
}

