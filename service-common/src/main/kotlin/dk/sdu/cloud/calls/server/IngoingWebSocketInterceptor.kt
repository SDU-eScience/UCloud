package dk.sdu.cloud.calls.server

import com.fasterxml.jackson.databind.JsonNode
import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.websocketOrNull
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
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
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*

class WSCall internal constructor(
    val session: WSSession,
    internal val frameNode: JsonNode,
    val streamId: String
) : IngoingCall {
    override val attributes = AttributeContainer()

    companion object : IngoingCallCompanion<WSCall> {
        override val klass = WSCall::class
        override val attributes = AttributeContainer()
    }
}

internal data class WSResponse<T>(val status: Int, val streamId: String, val payload: T)

class WSSession internal constructor(val id: String, val underlyingSession: WebSocketServerSession) {
    internal val onCloseHandlers = ArrayList<() -> Unit>()

    fun addOnCloseHandler(handler: () -> Unit) {
        onCloseHandlers.add(handler)
    }

    internal suspend fun rawSend(text: String) {
        underlyingSession.send(text)
    }

    suspend fun sendPayload(streamId: String, message: Any, statusCode: HttpStatusCode = HttpStatusCode.OK) {
        rawSend(defaultMapper.writeValueAsString(WSResponse(statusCode.value, streamId, message)))
    }

    suspend fun close(reason: String? = null) {
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

        engine.application.install(WebSockets)

        engine.application.routing {
            handlers.forEach { path, calls ->
                webSocket(path) {
                    val session = WSSession(UUID.randomUUID().toString(), this)
                    val callsByName = calls.associateBy { it.fullName }

                    try {
                        while (isActive) {
                            val frame = incoming.receive()
                            if (frame is Frame.Text) {
                                val text = frame.readText()

                                @Suppress("BlockingMethodInNonBlockingContext")
                                val parsedMessage = defaultMapper.readTree(text)

                                // We silently discard messages that don't follow the correct format
                                val requestedCall =
                                    parsedMessage["call"]?.takeIf { !it.isNull && it.isTextual }?.textValue() ?: continue

                                if (parsedMessage["payload"]?.isNull != false) continue

                                val streamId =
                                    parsedMessage["streamId"]?.takeIf { !it.isNull && it.isTextual }?.textValue()
                                        ?: continue

                                // We alert the caller if the send a well-formed message that we cannot handle
                                val call = callsByName[requestedCall]
                                if (call == null) {
                                    send(
                                        defaultMapper.writeValueAsString(
                                            WSResponse(
                                                HttpStatusCode.NotFound.value,
                                                streamId,
                                                Unit
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
                        session.onCloseHandlers.forEach { it() }

                        handlers.flatMap { it.value }.forEach { handler ->
                            handler.wsServerConfig.onClose?.invoke(session)
                        }
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
        return reader.readValue<R>(ctx.frameNode["payload"])
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

        val response = when (callResult) {
            is OutgoingCallResponse.Ok -> WSResponse(callResult.statusCode.value, ctx.streamId, callResult.result)
            is OutgoingCallResponse.Error -> WSResponse(callResult.statusCode.value, ctx.streamId, callResult.error)
            is OutgoingCallResponse.AlreadyDelivered -> null
        }

        if (response != null && typeRef != null) {
            val responseType = defaultMapper.typeFactory.constructParametricType(
                WSResponse::class.java,
                defaultMapper.typeFactory.constructType(typeRef)
            )

            ctx.session.rawSend(defaultMapper.writerFor(responseType).writeValueAsString(response))
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
