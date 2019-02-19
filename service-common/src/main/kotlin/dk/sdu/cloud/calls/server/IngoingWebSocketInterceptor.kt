package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.websocket
import dk.sdu.cloud.calls.websocketOrNull
import dk.sdu.cloud.service.Loggable
import io.ktor.application.install
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.consumeEachIndexed
import kotlinx.coroutines.channels.mapNotNull

class WSCall : IngoingCall {
    override val attributes = AttributeContainer()

    companion object : IngoingCallCompanion<WSCall> {
        override val klass = WSCall::class
        override val attributes = AttributeContainer()
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

        engine.application.install(WebSockets)

        engine.application.routing {
            handlers.forEach { path, handlers ->
                webSocket(path) {
                    log.info("Would install ${handlers.size} for $path")
                    log.info("Instead we are just echoing")

                    while (true) {
                        val frame = incoming.receive()
                        when (frame) {
                            is Frame.Text -> {
                                val text = frame.readText()
                                outgoing.send(Frame.Text("Echo: $text"))
                            }
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
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun <R : Any, S : Any, E : Any> produceResponse(
        ctx: WSCall,
        call: CallDescription<R, S, E>,
        callResult: OutgoingCallResponse<S, E>
    ) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object : Loggable {
        override val log = logger()
    }
}
