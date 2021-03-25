package dk.sdu.cloud.providers

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.handler.TextWebSocketHandler

private val defaultMapper = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    isLenient = true
    coerceInputValues = true
}

class UCloudWsContext<R : Any, S : Any, E : Any>(
    val call: CallDescription<R, S, E>,
    val session: WebSocketSession,
    val sendMessage: (S) -> Unit,
    val sendResponse: (response: S, statusCode: Int) -> Unit,
)

interface UCloudWSHandler {
    fun canHandleWebSocketCall(call: CallDescription<*, *, *>): Boolean
    fun <R : Any, S : Any, E : Any> dispatchToWebSocketHandler(
        ctx: UCloudWsContext<R, S, E>,
        request: R,
    )
}

@Configuration
@EnableWebSocket
class UCloudWsConfiguration(
    private val dispatcher: UCloudWsDispatcher
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        log.info("Registering UCloud WS dispatcher")
        registry
            .addHandler(dispatcher, "/ucloud/*/websocket")
            .setAllowedOrigins("*")
    }

    companion object : Loggable {
        override val log = logger()
    }
}

@Component
class UCloudWsDispatcher(
    private val verifier: UCloudJwtVerifier,
) : TextWebSocketHandler() {
    private val calls = ArrayList<CallDescription<*, *, *>>()
    private val handlers = ArrayList<UCloudWSHandler>()

    fun addContainer(container: CallDescriptionContainer) {
        calls.addAll(container.callContainer)
    }

    fun addHandler(handler: UCloudWSHandler) {
        handlers.add(handler)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val request = runCatching {
            defaultMapper.decodeFromString<WSRequest<JsonObject>>(message.payload)
        }.getOrNull() ?: return

        log.debug("Handling ${request.call}")

        @Suppress("UNCHECKED_CAST")
        val call = calls
            .find { it.websocketOrNull != null && it.fullName == request.call } as CallDescription<Any, Any, Any>?

        if (call == null) {
            log.debug("Unknown call: ${calls.map { it.fullName }}")
            session.sendMessage(
                TextMessage(
                    defaultMapper.encodeToString(
                        WSMessage.Response(request.streamId, Unit, 404)
                    )
                )
            )
        } else {
            val bearer = request.bearer
            if (bearer == null || !verifier.isValid(bearer)) {
                session.sendMessage(
                    TextMessage(defaultMapper.encodeToString(WSMessage.Response(request.streamId, Unit, 401)))
                )
            }
            val sendMessage = f@{ response: Any ->
                if (!session.isOpen) return@f
                session.sendMessage(
                    TextMessage(
                        defaultMapper.encodeToString(
                            WSMessage.Message.serializer(call.successType),
                            WSMessage.Message(request.streamId, response)
                        )
                    )
                )
            }

            val sendResponse = f@{ response: Any, statusCode: Int ->
                if (!session.isOpen) return@f
                session.sendMessage(
                    TextMessage(
                        defaultMapper.encodeToString(
                            WSMessage.Response.serializer(call.successType),
                            WSMessage.Response(request.streamId, response, statusCode)
                        )
                    )
                )
            }

            val wsCtx = UCloudWsContext(call, session, sendMessage, sendResponse)

            var didHandle = false
            for (handler in handlers) {
                if (handler.canHandleWebSocketCall(wsCtx.call)) {
                    handler.dispatchToWebSocketHandler(
                        wsCtx,
                        defaultMapper.decodeFromJsonElement(call.requestType, request.payload),
                    )
                    didHandle = true
                    break
                }
            }

            if (!didHandle) {
                log.debug("Call was not handled")
                session.sendMessage(
                    TextMessage(defaultMapper.encodeToString(WSMessage.Response(request.streamId, Unit, 404)))
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
