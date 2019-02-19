package dk.sdu.cloud.calls

class WebSocketRequest<R : Any, S : Any, E : Any>(
    val context: CallDescription<R, S, E>,
    val path: String
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
    get() = attributes.getOrNull(WebSocketRequest.callKey) as? WebSocketRequest<R, S, E>

// Builders

class WebSocketBuilder<R : Any, S : Any, E : Any>(val context: CallDescription<R, S, E>, val path: String) {
    fun build(): WebSocketRequest<R, S, E> {
        return WebSocketRequest(context, path)
    }
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.websocket(
    path: String,
    handler: WebSocketBuilder<R, S, E>.() -> Unit
) {
    attributes[WebSocketRequest.callKey] = WebSocketBuilder(this, path).also(handler).build()
}
