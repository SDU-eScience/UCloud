package dk.sdu.cloud.calls

import io.ktor.http.cio.websocket.WebSocketSession

class WebSocketRequest<R : Any, S : Any, E : Any> internal constructor(
    val context: CallDescription<R, S, E>,
    val path: String,
    val serverOnClose: ((WebSocketSession) -> Unit)?
) {
    companion object {
        internal val callKey = AttributeKey<WebSocketRequest<*, *, *>>("websocket-request")
    }
}

@Suppress("UNCHECKED_CAST")
val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.websocket: WebSocketRequest<R, S, E>
    get() = attributes[WebSocketRequest.callKey] as WebSocketRequest<R, S, E>

@Suppress("UNCHECKED_CAST")
val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.websocketOrNull: WebSocketRequest<R, S, E>?
    get() = attributes.getOrNull(WebSocketRequest.callKey) as WebSocketRequest<R, S, E>?

// Builders

class WebSocketBuilder<R : Any, S : Any, E : Any> internal constructor(
    val context: CallDescription<R, S, E>,
    val path: String
) {
    var serverOnClose: ((WebSocketSession) -> Unit)? = null

    fun build(): WebSocketRequest<R, S, E> {
        return WebSocketRequest(context, path, serverOnClose)
    }
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.websocket(
    path: String,
    handler: WebSocketBuilder<R, S, E>.() -> Unit = {}
) {
    attributes[WebSocketRequest.callKey] = WebSocketBuilder(this, path).also(handler).build()
}
