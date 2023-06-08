package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.AttributeKey
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.WSMessage
import dk.sdu.cloud.calls.WSRequest
import dk.sdu.cloud.calls.websocketOrNull
import dk.sdu.cloud.debug.DebugContextType
import dk.sdu.cloud.debugSystem
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.cio.ChannelReadException
import io.ktor.util.cio.ChannelWriteException
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import java.util.*

class WSCall internal constructor(
    val session: WSSession,
    internal val request: WSRequest<JsonObject>,
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

    internal suspend fun sendMessage(message: Any, typeRef: KSerializer<*>) {
        mutex.withLock {
            check(!hasSentResponse) { "Cannot send messages after response!" }

            session.sendMessage(streamId, message, typeRef)
        }
    }

    companion object : IngoingCallCompanion<WSCall> {
        override val klass = WSCall::class
        override val attributes = AttributeContainer()
    }
}

class WSSession internal constructor(val id: String, val underlyingSession: WebSocketServerSession) {
    val attributes = AttributeContainer()
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
        typeRef: KSerializer<*>
    ) {
        @Suppress("UNCHECKED_CAST")
        val serializer = WSMessage.Message.serializer(typeRef) as KSerializer<WSMessage.Message<*>>
        rawSend(defaultMapper.encodeToString(serializer, WSMessage.Message(streamId, message)))
    }

    suspend fun close(reason: String? = null) {
        isActive = false
        runCatching {
            underlyingSession.close(CloseReason(CloseReason.Codes.NORMAL, reason ?: "Unspecified reason"))
        }
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

    companion object : Loggable {
        override val log = logger()
    }
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
            //
            // NOTE(Dan):
            // We have now inserted our own ping from the server. This should hopefully keep the connection alive.

            pingPeriod = null
        }

        engine.application.routing {
            handlers.forEach { (path, calls) ->
                val requestSerializer = WSRequest.serializer(JsonObject.serializer())
                webSocket(path) {
                    log.trace("New websocket connection at $path")
                    val session = WSSession(UUID.randomUUID().toString(), this)
                    val callsByName = calls.associateBy { it.fullName }
                    var job: Job? = null

                    var nextPing = Time.now() + PING_PERIOD
                    try {
                        while (isActive && session.isActive) {
                            if (Time.now() >= nextPing) {
                                try {
                                    session.rawSend("""{"ping":"pong"}""")
                                } catch (ex: ClosedSendChannelException) {
                                    break
                                }
                                nextPing = Time.now() + PING_PERIOD
                            }

                            val frame = try {
                                withTimeout(1_000) {
                                    incoming.receive()
                                }
                            } catch (ex: TimeoutCancellationException) {
                                continue
                            }

                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                log.trace("Received frame: $text")

                                // We silently discard messages that don't follow the correct format
                                val parsedMessage = runCatching {
                                    defaultMapper.decodeFromString(requestSerializer, text)
                                }.getOrNull() ?: continue

                                log.trace("Parsed message: $parsedMessage")

                                val requestedCall = parsedMessage.call

                                log.trace("RequestedCall: $requestedCall")

                                val streamId = parsedMessage.streamId

                                log.trace("streamId: $streamId")

                                // We alert the caller if the send a well-formed message that we cannot handle
                                val call = callsByName[requestedCall]
                                if (call == null) {
                                    send(
                                        defaultMapper.encodeToString(
                                            WSMessage.Response.serializer(Unit.serializer()),
                                            WSMessage.Response(
                                                streamId,
                                                Unit,
                                                HttpStatusCode.NotFound.value
                                            )
                                        )
                                    )
                                    log.debug("Unknown call")
                                    continue
                                }

                                val ctx = WSCall(session, parsedMessage, streamId)
                                job = launch {
                                    debugSystem.useContext(DebugContextType.SERVER_REQUEST, call.fullName) {
                                        rpcServer.handleIncomingCall(
                                            this@IngoingWebSocketInterceptor,
                                            call,
                                            ctx
                                        )
                                    }
                                }
                            }
                        }
                    } catch (ex: Throwable) {
                        fun isChannelException(throwable: Throwable?) =
                            throwable is ChannelWriteException || throwable is ChannelReadException ||
                                    throwable is ClosedReceiveChannelException ||
                                    throwable is ClosedSendChannelException

                        if (isChannelException(ex) || isChannelException(ex.cause)) {
                            log.trace("Channel was closed")
                        } else {
                            log.info("Caught exception in websocket server handler")
                            log.info(ex.stackTraceToString())
                        }
                    } finally {
                        session.onCloseHandlers.forEach { it() }

                        handlers.flatMap { it.value }.forEach { handler ->
                            handler.wsServerConfig.onClose?.invoke(session)
                        }

                        session.close()

                        job?.cancel()
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
        return defaultMapper.decodeFromJsonElement(call.requestType, ctx.request.payload)
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

        if (payload != null && typeRef != null) {
            @Suppress("UNCHECKED_CAST")
            val response = defaultMapper.encodeToString(
                WSMessage.Response.serializer(typeRef.nullable) as KSerializer<WSMessage<*>>,
                WSMessage.Response<Any?>(
                    ctx.streamId,
                    payload,
                    callResult.statusCode.value
                )
            )

            ctx.responseHasBeenSent()

            try {
                ctx.session.rawSend(response)
            } catch (ex: ClosedSendChannelException) {
                // TODO For some reason the client won't notice that we are actually closing.
                runCatching { ctx.session.close() }
                throw ex
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        internal val callConfigKey = AttributeKey<WebsocketServerCallConfig>("ws-server-call-config")
        private const val PING_PERIOD = 1000L * 60
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
suspend fun <S : Any> CallHandler<*, S, *>.sendWSMessage(message: S, suppressClosedChannelException: Boolean = true) {
    withContext<WSCall> {
        try {
            ctx.sendMessage(message, description.successType)
        } catch (ex: ClosedSendChannelException) {
            if (!suppressClosedChannelException) {
                throw ex
            }
        }
    }
}
