package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.WSMessage
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

class OutgoingWSCall : OutgoingCall {
    override val attributes = AttributeContainer()

    companion object : OutgoingCallCompanion<OutgoingWSCall> {
        override val klass = OutgoingWSCall::class
        override val attributes = AttributeContainer()

        internal val SUBSCRIPTION_HANDLER_KEY = AttributeKey<suspend (Any) -> Unit>("ws-subscription-handler")
        val proxyAttribute = AttributeKey<String>("ucloud-username")
    }
}

expect fun atomicString(initialValue: String): AtomicString
expect class AtomicString{
    fun compareAndSet(expected: String, newValue: String): Boolean
    fun getAndSet(newValue: String): String
    fun getValue(): String
}
expect fun atomicInt(initialValue: Int): AtomicInteger
expect class AtomicInteger {
    fun incrementAndGet(): Int
    fun getAndIncrement(): Int
}

@OptIn(ExperimentalCoroutinesApi::class, KtorExperimentalAPI::class)
class OutgoingWSRequestInterceptor : OutgoingRequestInterceptor<OutgoingWSCall, OutgoingWSCall.Companion> {
    override val companion = OutgoingWSCall
    private val connectionPool = WSConnectionPool()
    private val streamId = atomicInt(0)
    private val sampleCounter = atomicInt(0)

    override suspend fun <R : Any, S : Any, E : Any> prepareCall(
        call: CallDescription<R, S, E>,
        request: R
    ): OutgoingWSCall {
        return OutgoingWSCall()
    }

    override suspend fun <R : Any, S : Any, E : Any> finalizeCall(
        call: CallDescription<R, S, E>,
        request: R,
        ctx: OutgoingWSCall
    ): IngoingCallResponse<S, E> {
        val callId = Random.nextInt(10000) // Non unique ID for logging
        val start = Time.now()
        val shortRequestMessage = request.toString().take(100)
        val streamId = streamId.getAndIncrement().toString()
        val shouldSample = sampleCounter.incrementAndGet() % SAMPLE_FREQUENCY == 0

        val url = run {
            val targetHost = ctx.attributes.outgoingTargetHost
            val host = targetHost.host.removeSuffix("/")

            // For some reason ktor's websocket client does not currently work when pointed at WSS, but works fine
            // when redirected from WS to WSS.
            val port = targetHost.takeIf { it.port != 443 }?.port ?: 80
            val scheme = "ws"

            val path = call.websocket.path.removePrefix("/")

            "$scheme://$host:$port/$path"
        }

        run {
            val requestDebug = "[$callId] -> ${call.fullName}: $shortRequestMessage"
            if (shouldSample) log.info(requestDebug)
            else log.debug(requestDebug)
        }

        val session = connectionPool.retrieveConnection(url, ctx.attributes.getOrNull(OutgoingWSCall.proxyAttribute))
        val wsRequest = WSRequest(
            call.fullName,
            streamId,
            request,
            bearer = ctx.attributes.outgoingAuthToken,
            project = ctx.project
        )
        val writer = WSRequest.serializer(call.requestType)
        val subscription = session.subscribe(streamId)
        val text = defaultMapper.encodeToString(writer, wsRequest)
        session.underlyingSession.outgoing.send(Frame.Text(text))

        val handler = ctx.attributes.getOrNull(OutgoingWSCall.SUBSCRIPTION_HANDLER_KEY)

        val response = coroutineScope {
            lateinit var response: WSMessage.Response<Any?>
            val channel = processMessagesFromStream(call, subscription, streamId)

            try {
                while (!channel.isClosedForReceive) {
                    val message = channel.receive()
                    if (message is WSMessage.Response) {
                        val responseDebug =
                            "[$callId] <- ${call.fullName} RESPONSE ${Time.now() - start}ms"

                        if (message.status in 400..599 || shouldSample) log.info(responseDebug)
                        else log.debug(responseDebug)

                        response = message
                        session.unsubscribe(streamId)
                        break
                    } else if (message is WSMessage.Message && handler != null) {
                        log.debug("[$callId] <- ${call.fullName} MESSAGE ${Time.now() - start}ms")
                        handler(message.payload!!)
                    }
                }
            } catch (ex: Throwable) {
                runCatching {
                    // Make sure the underlying session is also closed. Otherwise we risk that the connection
                    // pool won't renew this session.
                    session.underlyingSession.close()
                }

                if (ex is ClosedReceiveChannelException || ex is CancellationException) {
                    // Do nothing. It is expected that the channel will close down.
                    log.trace("Channel was closed")
                    response = WSMessage.Response(streamId, null, HttpStatusCode.BadGateway.value)
                } else {
                    throw ex
                }
            }

            response
        }

        @Suppress("UNCHECKED_CAST")
        return when (response.status) {
            in 100..399 -> IngoingCallResponse.Ok(response.payload as S, HttpStatusCode.fromValue(response.status), ctx)
            else -> IngoingCallResponse.Error(response.payload as E?, HttpStatusCode.fromValue(response.status), ctx)
        }
    }

    private fun CoroutineScope.processMessagesFromStream(
        call: CallDescription<*, *, *>,
        channel: ReceiveChannel<WSRawMessage>,
        streamId: String
    ): ReceiveChannel<WSMessage<Any?>> = produce {
        for (message in channel) {
            @Suppress("BlockingMethodInNonBlockingContext")
            val result = when (message.type) {
                null, WSMessage.MESSAGE_TYPE -> {
                    val payload = defaultMapper.decodeFromJsonElement(call.successType, message.payload)
                    WSMessage.Message<Any>(streamId, payload)
                }

                WSMessage.RESPONSE_TYPE -> {
                    val serializer = when (message.status) {
                        in 100..399 -> call.successType
                        else -> call.errorType
                    }

                    val payload = try {
                        defaultMapper.decodeFromJsonElement(serializer, message.payload)
                    } catch (ex: Throwable) {
                        if (message.status in 100..399) throw ex
                        null
                    }

                    WSMessage.Response(streamId, payload, message.status ?: 500)
                }
                else -> null
            }

            if (result != null) {
                @Suppress("UNCHECKED_CAST")
                send(result as WSMessage<Any?>)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        const val SAMPLE_FREQUENCY = 100
    }
}

@OptIn(ExperimentalCoroutinesApi::class, KtorExperimentalAPI::class)
internal class WSClientSession constructor(
    val underlyingSession: ClientWebSocketSession
) {
    private val channels = HashMap<String, Channel<WSRawMessage>>()
    private val mutex = Mutex()

    suspend fun subscribe(streamId: String): ReceiveChannel<WSRawMessage> {
        mutex.withLock {
            val channel = Channel<WSRawMessage>(Channel.UNLIMITED)
            channels[streamId] = channel
            return channel
        }
    }

    suspend fun unsubscribe(streamId: String) {
        mutex.withLock {
            channels.remove(streamId)?.cancel()
        }
    }

    fun startProcessing(scope: CoroutineScope) {
        scope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                while (true) {
                    val frame = underlyingSession.incoming.receive()

                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val frameNode = runCatching { defaultMapper.decodeFromString<WSRawMessage>(text) }.getOrNull()
                            ?: continue

                        mutex.withLock {
                            val channel = channels[frameNode.streamId] ?: return@withLock

                            if (!channel.isClosedForSend) {
                                try {
                                    channel.send(frameNode)
                                } catch (ex: CancellationException) {
                                    // Ignored
                                }
                            } else {
                                unsubscribe(frameNode.streamId)
                            }
                        }
                    }
                }
            } catch (ex: Throwable) {
                if (ex is ClosedReceiveChannelException || ex.cause is ClosedReceiveChannelException
                    || ex::class.simpleName == "CancellationException") {
                    mutex.withLock {
                        val emptyTree = JsonObject(emptyMap())

                        channels.forEach { (streamId, channel) ->
                            if (!channel.isClosedForSend) {
                                channel.send(WSRawMessage(WSMessage.RESPONSE_TYPE, 499, streamId, emptyTree))
                            }

                            channel.cancel()
                        }

                        channels.clear()
                    }
                } else {
                    log.info("Exception while processing client messages!")
                    log.info(ex.stackTraceToString())
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

@Serializable
internal data class WSRawMessage(
    val type: String? = null,
    val status: Int? = null,
    val streamId: String,
    val payload: JsonObject
)

expect fun createWebsocketClient(): HttpClient

@OptIn(ExperimentalCoroutinesApi::class, KtorExperimentalAPI::class)
internal class WSConnectionPool {
    private val connectionPool = HashMap<String, WSClientSession>()
    private val mutex = Mutex()
    private val websocketClient: HttpClient = createWebsocketClient()

    suspend fun retrieveConnection(
        location: String,
        proxiedTo: String?
    ): WSClientSession {
        val key = "$location/$proxiedTo"
        val existing = connectionPool[key]
        if (existing != null) {
            val realSession = existing.underlyingSession
            if (realSession.outgoing.isClosedForSend || realSession.incoming.isClosedForReceive) {
                connectionPool.remove(key)
            } else {
                return existing
            }
        }

        mutex.withLock {
            val existingAfterLock = connectionPool[key]
            if (existingAfterLock != null) return existingAfterLock

            val url = URLBuilder(location).build()
            log.info("Building new websocket connection to $url")
            val session = websocketClient.webSocketSession(
                host = url.host,
                port = url.port,
                path = url.fullPath,
                block = {
                    if (proxiedTo != null) {
                        header("UCloud-Username", proxiedTo)
                    }
                }
            )

            val wrappedSession = WSClientSession(session)
            connectionPool[key] = wrappedSession
            wrappedSession.startProcessing(GlobalScope)
            return wrappedSession
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

suspend fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.subscribe(
    request: R,
    authenticatedClient: AuthenticatedClient,
    handler: suspend (S) -> Unit
): IngoingCallResponse<S, E> {
    require(authenticatedClient.backend == OutgoingWSCall) {
        "subscribe call not supported for backend '${authenticatedClient.backend}'"
    }

    @Suppress("UNCHECKED_CAST")
    return authenticatedClient.client.call(
        this,
        request,
        authenticatedClient.backend as OutgoingCallCompanion<OutgoingCall>,
        beforeHook = { ctx ->
            authenticatedClient.authenticator(ctx)

            ctx.attributes[OutgoingWSCall.SUBSCRIPTION_HANDLER_KEY] = {
                handler(it as S)
            }
        },
        afterHook = authenticatedClient.afterHook,
    )
}
