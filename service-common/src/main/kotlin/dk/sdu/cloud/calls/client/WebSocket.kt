package dk.sdu.cloud.calls.client

import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.WSMessage
import dk.sdu.cloud.calls.websocket
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.TYPE_PROPERTY
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.websocket.ClientWebSocketSession
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.webSocketRawSession
import io.ktor.http.URLBuilder
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.http.fullPath
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.mapNotNull
import java.util.concurrent.ConcurrentHashMap

class OutgoingWSCall : OutgoingCall {
    override val attributes = AttributeContainer()

    companion object : OutgoingCallCompanion<OutgoingWSCall> {
        override val klass = OutgoingWSCall::class
        override val attributes = AttributeContainer()
    }
}

class OutgoingWSRequestInterceptor : OutgoingRequestInterceptor<OutgoingWSCall, OutgoingWSCall.Companion> {
    override val companion = OutgoingWSCall
    private val connectionPool = WSConnectionPool()

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
        val targetHost = ctx.attributes.outgoingTargetHost
        val host = targetHost.host.removeSuffix("/")
        val scheme = targetHost.scheme ?: "ws"
        val port = targetHost.port ?: if (scheme == "wss") 443 else 80
        val path = call.websocket.path.removeSuffix("/")

        val url = "$scheme://$host:$port/$path"

        val session = connectionPool.retrieveConnection(url) { configureNewSession(call, it) }
//        session.underlyingSession.outgoing.send(Frame.Text())
//        IngoingCallResponse.Ok()

        while (true) {
            val message = session.receiveChannel.receive()
            when (message) {
                is WSMessage.Response -> {
                    // Return normally, we should have parsed it correctly.
                }

                is WSMessage.Message -> {
                    // We should have registered a handler already which can accept this message.
                }
            }
        }
    }

    private suspend fun configureNewSession(
        call: CallDescription<*, *, *>,
        session: ClientWebSocketSession
    ): ReceiveChannel<WSMessage<Any>> {
        @Suppress("BlockingMethodInNonBlockingContext")
        return session.incoming.mapNotNull { frame ->
            if (frame !is Frame.Text) return@mapNotNull null
            val text = frame.readText()

            val frameNode = defaultMapper.readTree(text)

            val type = frameNode[TYPE_PROPERTY]?.takeIf { !it.isNull && it.isTextual }?.textValue()
            val status =
                frameNode[WSMessage.Response<*>::status.name]?.takeIf { !it.isNull && it.isIntegralNumber }?.intValue()

            when (type) {
                WSMessage.MESSAGE_TYPE -> {
                    defaultMapper.readerFor(call.successType).readValue<WSMessage.Message<Any>>(frameNode)
                }

                WSMessage.RESPONSE_TYPE -> {
                    val typeRef = when (status) {
                        in 100..399 -> call.successType
                        else -> call.errorType
                    }

                    defaultMapper.readerFor(typeRef).readValue<WSMessage<Any>>(frameNode)
                }
                else -> null
            }
        }
    }
}

internal data class WSClientSession(
    val underlyingSession: ClientWebSocketSession,
    val receiveChannel: ReceiveChannel<WSMessage<*>>
)

internal class WSConnectionPool {
    private val connectionPool = ConcurrentHashMap<String, WSClientSession>()
    @UseExperimental(KtorExperimentalAPI::class)
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    suspend fun retrieveConnection(
        location: String,
        configureNewInstance: suspend (ClientWebSocketSession) -> ReceiveChannel<WSMessage<*>>
    ): WSClientSession {
        val existing = connectionPool[location]
        if (existing != null) {
            if (existing.underlyingSession.outgoing.isClosedForSend ||
                existing.underlyingSession.incoming.isClosedForReceive
            ) {
                connectionPool.remove(location)
            } else {
                return existing
            }
        }

        val url = URLBuilder(location).build()
        val session = client.webSocketRawSession(
            host = url.host,
            port = url.port,
            path = url.fullPath
        )

        val wrappedSession = WSClientSession(session, configureNewInstance(session))
        connectionPool[location] = wrappedSession
        return wrappedSession
    }
}
