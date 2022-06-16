package dk.sdu.cloud.calls

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

class WebSocketRequest<R : Any, S : Any, E : Any> internal constructor(
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
    get() = attributes.getOrNull(WebSocketRequest.callKey) as WebSocketRequest<R, S, E>?

// Builders

class WebSocketBuilder<R : Any, S : Any, E : Any> internal constructor(
    val context: CallDescription<R, S, E>,
    val path: String
) {
    fun build(): WebSocketRequest<R, S, E> {
        return WebSocketRequest(context, path)
    }
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.websocket(
    path: String,
    handler: WebSocketBuilder<R, S, E>.() -> Unit = {}
) {
    attributes[WebSocketRequest.callKey] = WebSocketBuilder(this, path).also(handler).build()
}

@Serializable
data class WSRequest<T>(
    val call: String,
    val streamId: String,
    val payload: T,
    val bearer: String? = null,
    val causedBy: String? = null,
    val project: String? = null,
    val signedIntent: String? = null,
) {
    companion object {
        val CALL_FIELD = WSRequest<*>::call.name
        val STREAM_ID_FIELD = WSRequest<*>::streamId.name
        val PAYLOAD_FIELD = WSRequest<*>::payload.name
        val CAUSED_BY_FIELD = WSRequest<*>::causedBy.name
        val PROJECT_FIELD = WSRequest<*>::project.name
    }
}

/*
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(
        value = WSMessage.Response::class,
        name = WSMessage.RESPONSE_TYPE
    ),
    JsonSubTypes.Type(
        value = WSMessage.Message::class,
        name = WSMessage.MESSAGE_TYPE
    )
)
 */
@Serializable
sealed class WSMessage<T> {
    abstract val payload: T
    abstract val streamId: String

    @Serializable
    data class Response<T>(
        override val streamId: String,
        override val payload: T,
        val status: Int,
        val type: String = RESPONSE_TYPE
    ) : WSMessage<T>()

    @Serializable
    data class Message<T>(
        override val streamId: String,
        override val payload: T,
        val type: String = MESSAGE_TYPE
    ) : WSMessage<T>()

    companion object {
        const val RESPONSE_TYPE = "response"
        const val MESSAGE_TYPE = "message"

        val PAYLOAD_FIELD = WSMessage<*>::payload.name
        val STREAM_ID_FIELD = WSMessage<*>::streamId.name
        val STATUS_FIELD = WSMessage.Response<*>::status.name
    }
}
