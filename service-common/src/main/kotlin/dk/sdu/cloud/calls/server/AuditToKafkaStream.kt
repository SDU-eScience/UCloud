package dk.sdu.cloud.calls.server

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.calls.AuditDescription
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auditOrNull
import dk.sdu.cloud.calls.jvmClass
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.events.JsonEventStream
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.TokenValidation
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.request.header
import io.ktor.util.date.toGMTDate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.util.*

private typealias HttpEventProducer = EventProducer<HttpCallLogEntry>
private typealias AuditProducer = EventProducer<AuditEvent<*>>

private class AuditCallEventStream(call: CallDescription<*, *, *>) : EventStream<AuditEvent<*>> {
    override val name = "audit.${call.namespace}"
    override val desiredPartitions: Int? = null
    override val desiredReplicas: Short? = null
    override val keySelector: (AuditEvent<*>) -> String = { it.http.jobId }

    private val writer = defaultMapper.writerFor(call.auditJavaType)
    private val reader = defaultMapper.readerFor(call.auditJavaType)

    override fun serialize(event: AuditEvent<*>): String {
        return writer.writeValueAsString(event)
    }

    override fun deserialize(value: String): AuditEvent<*> {
        return reader.readValue(value)
    }
}

@Deprecated("Renamed")
typealias AuditToKafkaStream = AuditToEventStream

class AuditToEventStream(
    private val instance: ServiceInstance,
    private val eventStreamService: EventStreamService,
    private val tokenValidation: TokenValidation<Any>
) {
    private val eventProducerCache = HashMap<CallDescription<*, *, *>, HttpEventProducer>()
    private val auditProducerCache = HashMap<CallDescription<*, *, *>, AuditProducer>()

    private fun httpEventProducer(call: CallDescription<*, *, *>): HttpEventProducer {
        synchronized(this) {
            val cached = eventProducerCache[call]
            if (cached != null) return cached

            val eventProducer = eventStreamService.createProducer(httpLogsStream)
            eventProducerCache[call] = eventProducer
            return eventProducer
        }
    }

    private fun auditProducer(call: CallDescription<*, *, *>): AuditProducer {
        synchronized(this) {
            val cached = auditProducerCache[call]
            if (cached != null) return cached

            val auditStreamProducersOnly = AuditCallEventStream(call)
            val auditProducer = eventStreamService.createProducer(auditStreamProducersOnly)
            auditProducerCache[call] = auditProducer
            return auditProducer
        }
    }

    fun register(server: RpcServer) {
        server.attachFilter(object : IngoingCallFilter.BeforeParsing() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true

            override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>) {
                context.audit = AuditData(System.currentTimeMillis())
            }
        })

        server.attachFilter(object : IngoingCallFilter.AfterResponse() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true
            override suspend fun run(
                context: IngoingCall,
                call: CallDescription<*, *, *>,
                request: Any?,
                result: OutgoingCallResponse<*, *>
            ) {
                val auditDescription = call.auditOrNull
                val auditData = context.audit
                val eventProducer = httpEventProducer(call)
                val auditProducer = auditProducer(call)

                val bearerToken = context.bearer
                val requestContentLength =
                    (context as? HttpCall)?.call?.request?.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0
                val causedBy = context.causedBy
                val remoteOrigin = context.remoteHost
                val userAgent = context.userAgent

                val startTime = auditData.requestStart
                val responseTime =
                    if (auditDescription?.longRunningResponseTime == true) 0L
                    else System.currentTimeMillis() - startTime

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
                        // We can, in this case, still produce a message. But only because the audit payload will be
                        // null.
                    } else {
                        val expectedType = auditDescription.auditType.type.jvmClass
                        if (expectedType != auditPayload.javaClass) {
                            repeat(5) {
                                log.warn(
                                    "Audit payload does not match the expected type. " +
                                            "We got ${auditPayload.javaClass} but expected $expectedType"
                                )
                                log.warn("No audit trace has been produced")
                            }
                            // We cannot create an audit track since it will mess up the resulting elastic
                            // index (bad type)
                            return
                        }
                    }
                }

                val expiresPeriod = auditDescription?.retentionPeriod ?: AuditDescription.DEFAULT_RETENTION_PERIOD
                val expiry = ZonedDateTime.now().plus(expiresPeriod).toGMTDate().timestamp

                coroutineScope {
                    launch {
                        val entry = HttpCallLogEntry(
                            jobId = context.jobId,
                            handledBy = instance,
                            causedBy = causedBy,
                            requestName = call.fullName,
                            userAgent = userAgent,
                            remoteOrigin = remoteOrigin ?: throw IllegalStateException("Unknown remote origin"),
                            token = token,
                            requestSize = requestContentLength,
                            requestJson = auditPayload,
                            responseCode = responseCode,
                            responseTime = responseTime,
                            expiry = expiry
                        )

                        eventProducer.produce(entry)
                        auditProducer.produce(AuditEvent(entry, auditPayload))
                    }
                }
            }
        })
    }

    companion object : Loggable {
        override val log = logger()

        private val httpLogsStream = JsonEventStream<HttpCallLogEntry>("http.logs", jacksonTypeRef(), { it.jobId })
    }
}

private val auditJavaTypeCache: MutableMap<String, JavaType> =
    Collections.synchronizedMap(HashMap<String, JavaType>())

private val CallDescription<*, *, *>.auditJavaType: JavaType
    get() {
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

val CallDescriptionContainer.auditStream: EventStream<String>
    get() = object : EventStream<String> {
        override val name: String = "audit.$namespace"
        override val desiredPartitions: Int? = null
        override val desiredReplicas: Short? = null
        override val keySelector: (String) -> String = { it }

        override fun serialize(event: String): String {
            return event
        }

        override fun deserialize(value: String): String {
            return value
        }
    }

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

