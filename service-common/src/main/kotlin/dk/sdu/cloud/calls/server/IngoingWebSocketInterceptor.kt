package dk.sdu.cloud.calls.server

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.WSMessage
import dk.sdu.cloud.calls.WSRequest
import dk.sdu.cloud.calls.websocketOrNull
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TYPE_PROPERTY
import io.ktor.application.install
import io.ktor.http.HttpStatusCode
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.http.cio.websocket.send
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.websocket.WebSocketServerSession
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.util.*

class WSCall internal constructor(
    val session: WSSession,
    internal val frameNode: JsonNode,
    val streamId: String
) : IngoingCall {
    override val attributes = AttributeContainer()
    private var hasSentResponse = false
    private val mutex = Mutex()

    internal suspend fun responseHasBeenSent() {
        mutex.withLock {
            hasSentResponse = true
        }
    }

    internal suspend fun sendMessage(message: Any, typeRef: TypeReference<*>) {
        mutex.withLock {
            if (hasSentResponse) throw IllegalStateException("Cannot send messages after response!")

            session.sendMessage(streamId, message, typeRef)
        }
    }

    companion object : IngoingCallCompanion<WSCall> {
        override val klass = WSCall::class
        override val attributes = AttributeContainer()
    }
}

class WSSession internal constructor(val id: String, val underlyingSession: WebSocketServerSession) {
    internal val onCloseHandlers = ArrayList<suspend () -> Unit>()
    internal var isActive: Boolean = true
        private set

    fun addOnCloseHandler(handler: suspend () -> Unit) {
        onCloseHandlers.add(handler)
    }

    internal suspend fun rawSend(text: String) {
        underlyingSession.send(text)
    }

    internal suspend fun sendMessage(
        streamId: String,
        message: Any,
        typeRef: TypeReference<*>
    ) {
        val node = defaultMapper.readerFor(typeRef).readTree(
            defaultMapper.writerFor(typeRef).writeValueAsString(message)
        )

        val payloadTree = defaultMapper.writeValueAsString(
            mapOf<String, Any>(
                TYPE_PROPERTY to WSMessage.MESSAGE_TYPE,
                WSMessage.STREAM_ID_FIELD to streamId,
                WSMessage.PAYLOAD_FIELD to node
            )
        )

        rawSend(payloadTree)
    }

    suspend fun close(reason: String? = null) {
        isActive = false
        underlyingSession.close(CloseReason(CloseReason.Codes.NORMAL, reason ?: "Unspecified reason"))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WSSession

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String = "WSSession($id)"
}

class IngoingWebSocketInterceptor(
    private val engine: ApplicationEngine,
    private val rpcServer: RpcServer
) : IngoingRequestInterceptor<WSCall, WSCall.Companion> {
    override val companion = WSCall
    private val handlers: MutableMap<String, List<CallDescription<*, *, *>>> = HashMap()

    override fun onStart() {
        if (handlers.isEmpty()) return

        engine.application.install(WebSockets) {
            // NOTE(Dan):
            // We explicitly disable pings. As of ktor 1.1.5 this caused about 0.5-1% of requests to randomly get
            // dropped. We assume that this was due to the ping/pong protocol not being correctly implemented. We don't
            // know which side dropped the ball. This may also have been due to bad concurrency from our side.
            //
            // Setting pingPeriod to null fixes all of this. It does, however, mean that a lot of open subscriptions
            // will timeout after some time (whenever the load balancer decides).

            pingPeriod = null
        }

        engine.application.routing {
            handlers.forEach { (path, calls) ->
                webSocket(path) {
                    log.info("New websocket connection at $path")
                    val session = WSSession(UUID.randomUUID().toString(), this)
                    val callsByName = calls.associateBy { it.fullName }

                    try {
                        while (isActive && session.isActive) {
                            val frame = try {
                                withTimeout(1_000) {
                                    incoming.receive()
                                }
                            } catch (ex: TimeoutCancellationException) {
                                continue
                            }

                            if (frame is Frame.Text) {
                                val text = frame.readText()

                                @Suppress("BlockingMethodInNonBlockingContext")
                                val parsedMessage = defaultMapper.readTree(text)

                                // We silently discard messages that don't follow the correct format
                                val requestedCall =
                                    parsedMessage[WSRequest.CALL_FIELD]
                                        ?.takeIf { !it.isNull && it.isTextual }
                                        ?.textValue()
                                        ?: continue

                                if (parsedMessage[WSRequest.PAYLOAD_FIELD]?.isNull != false) continue

                                val streamId =
                                    parsedMessage[WSRequest.STREAM_ID_FIELD]
                                        ?.takeIf { !it.isNull && it.isTextual }
                                        ?.textValue()
                                        ?: continue

                                // We alert the caller if the send a well-formed message that we cannot handle
                                val call = callsByName[requestedCall]
                                if (call == null) {
                                    send(
                                        defaultMapper.writeValueAsString(
                                            WSMessage.Response(
                                                streamId,
                                                Unit,
                                                HttpStatusCode.NotFound.value
                                            )
                                        )
                                    )
                                    continue
                                }

                                launch {
                                    rpcServer.handleIncomingCall(
                                        this@IngoingWebSocketInterceptor,
                                        call,
                                        WSCall(session, parsedMessage, streamId)
                                    )
                                }
                            }
                        }
                    } catch (ex: ClosedReceiveChannelException) {
                        log.debug("Channel was closed")
                    } finally {
                        log.debug("Receive channel is closing down!")
                        session.onCloseHandlers.forEach { it() }

                        handlers.flatMap { it.value }.forEach { handler ->
                            handler.wsServerConfig.onClose?.invoke(session)
                        }

                        session.close()
                    }
                }
            }
        }
    }

    override fun addCallListenerForCall(call: CallDescription<*, *, *>) {
        // WebSocket calls that share the same path will be installed into the same handler.
        // For this reason we will not install the concrete handler until onStart() is called.
        val websocket = call.websocketOrNull ?: return
        synchronized(this) {
            handlers[websocket.path] = (handlers[websocket.path] ?: emptyList()) + listOf(call)
        }
    }

    override suspend fun <R : Any> parseRequest(ctx: WSCall, call: CallDescription<R, *, *>): R {
        val reader = defaultMapper.readerFor(call.requestType)

        @Suppress("BlockingMethodInNonBlockingContext")
        return reader.readValue<R>(ctx.frameNode[WSRequest.PAYLOAD_FIELD])
    }

    override suspend fun <R : Any, S : Any, E : Any> produceResponse(
        ctx: WSCall,
        call: CallDescription<R, S, E>,
        callResult: OutgoingCallResponse<S, E>
    ) {
        val typeRef = when (callResult) {
            is OutgoingCallResponse.Ok -> call.successType
            is OutgoingCallResponse.Error -> call.errorType
            is OutgoingCallResponse.AlreadyDelivered -> null
        }

        val payload: Any? = when (callResult) {
            is OutgoingCallResponse.Ok -> callResult.result
            is OutgoingCallResponse.Error -> callResult.error
            is OutgoingCallResponse.AlreadyDelivered -> return
        }

        log.debug("payload=$payload")

        if (payload != null && typeRef != null) {
            val node = defaultMapper.readerFor(typeRef).readTree(
                defaultMapper.writerFor(typeRef).writeValueAsString(payload)
            )

            val response = defaultMapper.writeValueAsString(
                mapOf(
                    WSMessage.STREAM_ID_FIELD to ctx.streamId,
                    TYPE_PROPERTY to WSMessage.RESPONSE_TYPE,
                    WSMessage.STATUS_FIELD to callResult.statusCode.value,
                    WSMessage.PAYLOAD_FIELD to node
                )
            )

            log.debug(response)
            ctx.responseHasBeenSent()

            try {
                ctx.session.rawSend(response)
            } catch (ex: ClosedSendChannelException) {
                // TODO For some reason the client won't notice that we are actually closing.
                ctx.session.close()
                throw ex
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        internal val callConfigKey = AttributeKey<WebsocketServerCallConfig>("ws-server-call-config")
    }
}

class WebsocketServerCallConfig {
    var onClose: ((WSSession) -> Unit)? = null
}

val CallDescription<*, *, *>.wsServerConfig: WebsocketServerCallConfig
    get() {
        val current = attributes.getOrNull(IngoingWebSocketInterceptor.callConfigKey)
        if (current != null) return current
        val config = WebsocketServerCallConfig()
        attributes[IngoingWebSocketInterceptor.callConfigKey] = config
        return config
    }

// TODO This is not using the correct writer
suspend fun <S : Any> CallHandler<*, S, *>.sendWSMessage(message: S) {
    withContext<WSCall> {
        ctx.sendMessage(message, description.successType)
    }
}
