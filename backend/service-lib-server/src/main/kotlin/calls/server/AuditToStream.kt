package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.AuditDescription
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auditOrNull
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.events.JsonEventStream
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.TokenValidation
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

private typealias HttpEventProducer = EventProducer<HttpCallLogEntry>
private typealias AuditProducer = EventProducer<AuditEvent<*>>

private class AuditCallEventStream(call: CallDescription<*, *, *>) : EventStream<AuditEvent<*>> {
    override val name = "audit.${call.namespace}"
    override val desiredPartitions: Int? = null
    override val desiredReplicas: Short? = null
    override val keySelector: (AuditEvent<*>) -> String = { it.http.jobId }

    @Suppress("UNCHECKED_CAST")
    private val serializer: KSerializer<Any> = call.auditOrNull?.auditType ?: call.requestType as KSerializer<Any>

    override fun serialize(event: AuditEvent<*>): String {
        @Suppress("UNCHECKED_CAST")
        return defaultMapper.encodeToString(AuditEvent.serializer(serializer), event as AuditEvent<Any>)
    }

    override fun deserialize(value: String): AuditEvent<*> {
        @Suppress("UNCHECKED_CAST")
        return defaultMapper.decodeFromString(AuditEvent.serializer(serializer), value)
    }
}

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
                context.audit = AuditData(Time.now())
            }
        })

        server.attachFilter(object : IngoingCallFilter.AfterResponse() {
            override fun canUseContext(ctx: IngoingCall): Boolean = true
            override suspend fun run(
                context: IngoingCall,
                call: CallDescription<*, *, *>,
                request: Any?,
                result: OutgoingCallResponse<*, *>,
                responseTimeMs: Long
            ) {
                val auditDescription = call.auditOrNull
                val auditData = context.auditOrNull ?: return
                if (auditData.skipAuditing) return

                val eventProducer = httpEventProducer(call)
                val auditProducer = auditProducer(call)

                val bearerToken = context.bearer
                val requestContentLength =
                    (context as? HttpCall)?.ktor?.call?.request?.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0
                val causedBy = context.causedBy
                val remoteOrigin = context.remoteHost
                val userAgent = context.userAgent

                val startTime = auditData.requestStart
                val responseTime =
                    if (auditDescription?.longRunningResponseTime == true) 0L
                    else Time.now() - startTime

                val responseCode = result.statusCode.value

                val token = run {
                    val principalFromBearerToken = bearerToken
                        ?.let { tokenValidation.validateOrNull(it) }
                        ?.let { tokenValidation.decodeToken(it) }

                    val principalFromOverride = auditData.securityPrincipalTokenToAudit
                    principalFromOverride ?: principalFromBearerToken
                }

                val auditPayload = auditData.requestToAudit ?: request

                val expiresPeriod = auditDescription?.retentionPeriod ?: AuditDescription.DEFAULT_RETENTION_PERIOD
                val expiry = Time.now() + expiresPeriod
                val auditSerializer = (auditDescription?.auditType ?: call.requestType) as KSerializer<Any?>

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
                            responseCode = responseCode,
                            responseTime = responseTime,
                            expiry = expiry,
                            project = context.project,
                            requestJson = defaultMapper.encodeToJsonElement(auditSerializer, auditPayload)
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

        private val httpLogsStream = JsonEventStream("http.logs", HttpCallLogEntry.serializer(), { it.jobId })
    }
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
