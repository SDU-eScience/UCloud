package dk.sdu.cloud.calls.client

import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.WSMessage
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.systemName
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
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
        val signedIntentAttribute = AttributeKey<String>("ucloud-signed-intent")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
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
            val port = targetHost.port ?: if (targetHost.scheme == "https") 443 else 80
            val scheme = when {
                targetHost.scheme == "http" -> "ws"
                targetHost.scheme == "https" -> "wss"
                port == 80 -> "ws"
                port == 443 -> "wss"
                else -> "ws"
            }

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
            project = ctx.project,
            signedIntent = ctx.attributes.getOrNull(OutgoingWSCall.signedIntentAttribute),
        )
        val writer = WSRequest.serializer(call.requestType)
        val subscription = session.subscribe(streamId)
        val text = defaultMapper.encodeToString(writer, wsRequest)
        session.underlyingSession.outgoing.send(Frame.Text(text))

        val handler = ctx.attributes.getOrNull(OutgoingWSCall.SUBSCRIPTION_HANDLER_KEY)

        val response = coroutineScope {
            requestCounter.labels(call.fullName).inc()
            requestsInFlight.labels(call.fullName).inc()

            try {
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

                            if (message.status in 400..599) requestsErrorCounter.labels(call.fullName).inc()
                            else requestsSuccessCounter.labels(call.fullName).inc()

                            response = message
                            session.unsubscribe(streamId)
                            break
                        } else if (message is WSMessage.Message && handler != null) {
                            log.debug("[$callId] <- ${call.fullName} MESSAGE ${Time.now() - start}ms")
                            handler(message.payload!!)

                            requestsMessageCounter.labels(call.fullName).inc()
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
            } finally {
                requestsInFlight.labels(call.fullName).dec()
            }
        }

        @Suppress("UNCHECKED_CAST")
        return when (response.status) {
            in 100..399 -> IngoingCallResponse.Ok(response.payload as S, HttpStatusCode.parse(response.status), ctx)
            else -> IngoingCallResponse.Error(response.payload as E?, HttpStatusCode.parse(response.status), ctx)
        }
    }

    private fun CoroutineScope.processMessagesFromStream(
        call: CallDescription<*, *, *>,
        channel: ReceiveChannel<WSRawMessage>,
        streamId: String
    ): ReceiveChannel<WSMessage<Any?>> = produce(capacity = Channel.UNLIMITED) {
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

        private val requestCounter = Counter.build()
            .namespace(systemName)
            .subsystem("rpc_client_ws")
            .name("requests_started")
            .help("Total number of requests passing through RpcClient with a WS backend")
            .labelNames("request_name")
            .register()

        private val requestsSuccessCounter = Counter.build()
            .namespace(systemName)
            .subsystem("rpc_client_ws")
            .name("requests_success")
            .help("Total number of requests which has passed through RpcClient successfully with a WS backend")
            .labelNames("request_name")
            .register()

        private val requestsErrorCounter = Counter.build()
            .namespace(systemName)
            .subsystem("rpc_client_ws")
            .name("requests_error")
            .help("Total number of requests which has passed through RpcClient with a failure with a WS backend")
            .labelNames("request_name")
            .register()

        private val requestsMessageCounter = Counter.build()
            .namespace(systemName)
            .subsystem("rpc_client_ws")
            .name("messages")
            .help("Total number of messages which has been sent (more than one message per request is possible)")
            .labelNames("request_name")
            .register()

        private val requestsInFlight = Gauge.build()
            .namespace(systemName)
            .subsystem("rpc_client_ws")
            .name("requests_in_flight")
            .help("Number of requests currently in-flight in the RpcClient with a WS backend")
            .labelNames("request_name")
            .register()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
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
                        val frameNode = runCatching { defaultMapper.decodeFromString(WSRawMessage.serializer(), text) }.getOrNull()
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
            val session = websocketClient.webSocketSession {
                url(url)

                if (proxiedTo != null) {
                    header("UCloud-Username", base64Encode(proxiedTo.encodeToByteArray()))
                }
            }

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
