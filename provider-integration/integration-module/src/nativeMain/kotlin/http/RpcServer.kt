package dk.sdu.cloud.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.http.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject

class ConnectionData(
    val connectionId: Int
)

const val version = "2022.1.0"

data class HttpContext(
    val payload: ByteBuffer,
    val headers: List<Header>,
    val session: HttpClientSession<ConnectionData>,
)

data class WebSocketContext<R : Any, S : Any, E : Any>(
    val internalId: Int,
    val rawRequest: WSRequest<JsonObject>,
    val call: CallDescription<R, S, E>,
    val sendMessage: (S) -> Unit,
    val sendResponse: (response: S, statusCode: Int) -> Unit,
    val isOpen: () -> Boolean,
)

class RpcServer(
    private val port: Int,
    private val showWelcomeMessage: Boolean = true,
) {
    private val handlerBuilder = ArrayList<CallWithHandler<*, *, *>>()
    val handlers: List<CallWithHandler<*, *, *>>
        get() = handlerBuilder

    fun <R : Any, S : Any, E : Any> implement(
        call: CallDescription<R, S, E>,
        handler: suspend CallHandler<R, S, E>.() -> OutgoingCallResponse<S, E>,
    ) {
        handlerBuilder.add(CallWithHandler(call, handler))
    }

    fun start() {
        if (showWelcomeMessage) {
            println(buildString {
                append("\u001B[34m")
                append(
                    """
                         __  __  ____    ___                      __     
                        /\ \/\ \/\  _`\ /\_ \                    /\ \    
                        \ \ \ \ \ \ \/\_\//\ \     ___   __  __  \_\ \      Version $version
                         \ \ \ \ \ \ \/_/_\ \ \   / __`\/\ \/\ \ /'_` \     Running on http://localhost:$port
                          \ \ \_\ \ \ \L\ \\_\ \_/\ \L\ \ \ \_\ /\ \L\ \ 
                           \ \_____\ \____//\____\ \____/\ \____\ \___,_\
                            \/_____/\/___/ \/____/\/___/  \/___/ \/__,_ /
                                                             
                    """.trimIndent()
                )
                append("\u001B[0m")
            })
        }

        var idGenerator = atomic(0)
        val contentTypeJson = listOf(Header("Content-Type", "application/json"))
        startServer(
            port,
            { ConnectionData(idGenerator.getAndIncrement()) },
            httpRequestHandler = object : HttpRequestHandler<ConnectionData> {
                override fun HttpClientSession<ConnectionData>.handleRequest(
                    method: HttpMethod,
                    path: String,
                    headers: List<Header>,
                    payload: ByteBuffer
                ) {
                    println("$method $path")
                    var foundCall: CallWithHandler<Any, Any, Any>? = null
                    for (callWithHandler in handlers) {
                        val (call) = callWithHandler
                        if (call.httpOrNull == null) continue
                        if (call.http.method != method) continue

                        val expectedPath =
                            (call.http.path.basePath.removeSuffix("/") + "/" +
                                call.http.path.segments.joinToString("/") {
                                    when (it) {
                                        is HttpPathSegment.Simple -> it.text
                                    }
                                }).removePrefix("/").removeSuffix("/").let { "/$it" }
                        if (path.substringBefore('?') != expectedPath) continue

                        @Suppress("UNCHECKED_CAST")
                        foundCall = callWithHandler as CallWithHandler<Any, Any, Any>
                        break
                    }

                    if (foundCall == null) {
                        sendHttpResponse(404, defaultHeaders())
                        return
                    }

                    try {
                        val (call, handler) = foundCall
                        val http = call.http
                        val requestMessage = try {
                            when {
                                call.requestType == Unit.serializer() -> {
                                    @Suppress("RedundantUnitExpression")
                                    Unit
                                }

                                http.body is HttpBody.BoundToEntireRequest<*> -> {
                                    if (payload.readerRemaining() <= 0) {
                                        sendHttpResponse(400, defaultHeaders())
                                        return
                                    }

                                    val arr = ByteArray(payload.readerRemaining())
                                    val bytes = payload.get(arr)
                                    val entireMessage = arr.decodeToString(endIndex = bytes)

                                    defaultMapper.decodeFromString(call.requestType, entireMessage)
                                }

                                http.params?.parameters?.isNotEmpty() == true -> {
                                    val query = path.substringAfter('?')
                                    ParamsParsing(query, call).decodeSerializableValue(call.requestType)
                                }

                                else -> {
                                    error("Unable to parse request")
                                }
                            }
                        } catch (ex: Throwable) {
                            sendHttpResponse(400, defaultHeaders())
                            return
                        }

                        log.debug("Incoming call: ${call.fullName} [$requestMessage]")
                        val context = CallHandler(
                            IngoingCall(AttributeContainer(), HttpContext(payload, headers, this)),
                            requestMessage,
                            call
                        )
                        middlewares.value.forEach { it.beforeRequest(context) }
                        runBlocking {
                            when (val outgoingResult = handler.invoke(context)) {
                                is OutgoingCallResponse.Ok -> {
                                    val encodedPayload = defaultMapper
                                        .encodeToString(call.successType, outgoingResult.result)
                                        .encodeToByteArray()

                                    sendHttpResponseWithData(
                                        200,
                                        contentTypeJson,
                                        encodedPayload
                                    )
                                }

                                is OutgoingCallResponse.Error -> {
                                    val error = outgoingResult.error
                                    val encodedPayload = (if (error != null) {
                                        defaultMapper.encodeToString(call.errorType, outgoingResult.error)
                                    } else {
                                        "{}"
                                    }).encodeToByteArray()

                                    sendHttpResponseWithData(
                                        outgoingResult.statusCode.value,
                                        contentTypeJson,
                                        encodedPayload
                                    )
                                }

                                is OutgoingCallResponse.AlreadyDelivered -> {
                                    // Do nothing
                                }
                            }
                        }
                    } catch (ex: Throwable) {
                        if (ex is RPCException) {
                            sendHttpResponseWithData(
                                ex.httpStatusCode.value,
                                contentTypeJson,
                                defaultMapper
                                    .encodeToString(CommonErrorMessage(ex.why, ex.errorCode))
                                    .encodeToByteArray()
                            )
                        } else {
                            log.warn(
                                "Caught an unexpected error in ${foundCall.call.fullName}\n" +
                                    ex.stackTraceToString()
                            )
                            sendHttpResponse(500, defaultHeaders())
                        }
                    }
                }
            },
            webSocketRequestHandler = object : WebSocketRequestHandler<ConnectionData> {
                override fun HttpClientSession<ConnectionData>.handleTextFrame(frame: String) {
                    val request = runCatching {
                        defaultMapper.decodeFromString<WSRequest<JsonObject>>(frame)
                    }.getOrNull() ?: run {
                        log.debug("Invalid request $frame")
                        return
                    }

                    @Suppress("UNCHECKED_CAST")
                    val foundCall = handlers
                        .find { it.call.websocketOrNull != null && it.call.fullName == request.call }
                        as CallWithHandler<Any, Any, Any>?

                    if (foundCall == null) {
                        outputScratch.clear()
                        outputScratch.put(
                            defaultMapper
                                .encodeToString(WSMessage.Response(request.streamId, Unit, 404))
                                .encodeToByteArray()
                        )
                        sendWebsocketFrame(WebSocketOpCode.TEXT, outputScratch)
                        log.debug("Call not found")
                        return
                    }

                    @Suppress("UNCHECKED_CAST")
                    val call = foundCall.call

                    val parsedRequest = runCatching {
                        defaultMapper.decodeFromJsonElement(call.requestType, request.payload)
                    }.getOrNull() ?: run {
                        outputScratch.clear()
                        outputScratch.put(
                            defaultMapper
                                .encodeToString(WSMessage.Response(request.streamId, Unit, 400))
                                .encodeToByteArray()
                        )
                        sendWebsocketFrame(WebSocketOpCode.TEXT, outputScratch)
                        log.debug("Invalid request message")
                        return
                    }

                    val isOpen: () -> Boolean = { !closing }
                    val sendMessage: (Any) -> Unit = {
                        if (!isOpen()) {
                            throw RPCException(
                                "Connection already closed",
                                HttpStatusCode(499, "Connection already closed")
                            )
                        }

                        threadLocalBuffer1.clear()
                        threadLocalBuffer1.put(
                            defaultMapper
                                .encodeToString(
                                    WSMessage.Message.serializer(call.successType),
                                    WSMessage.Message(request.streamId, it)
                                )
                                .encodeToByteArray()
                        )
                        sendWebsocketFrame(WebSocketOpCode.TEXT, threadLocalBuffer1)
                    }

                    val sendResponse: (Any?, Int) -> Unit = { message, statusCode ->
                        if (!isOpen()) {
                            log.debug("Connection is no longer open")
                            throw RPCException(
                                "Connection already closed",
                                HttpStatusCode(499, "Connection already closed")
                            )
                        }

                        threadLocalBuffer1.clear()
                        threadLocalBuffer1.put(
                            defaultMapper
                                .encodeToString(
                                    WSMessage.Response.serializer(
                                        if (statusCode in 200..299) {
                                            call.successType
                                        } else {
                                            call.errorType
                                        }
                                    ),
                                    WSMessage.Response(request.streamId, message ?: Unit, statusCode)
                                )
                                .encodeToByteArray()
                        )
                        sendWebsocketFrame(WebSocketOpCode.TEXT, threadLocalBuffer1)
                    }

                    val wsContext = WebSocketContext(
                        appData.connectionId,
                        request,
                        foundCall.call,
                        sendMessage,
                        sendResponse,
                        isOpen
                    )


                    log.debug("Incoming call: ${foundCall.call.fullName} (${request.toString().take(240)})")
                    val context = CallHandler(IngoingCall(AttributeContainer(), wsContext), parsedRequest, call)
                    middlewares.value.forEach { it.beforeRequest(context) }

                    runBlocking {
                        try {
                            when (val outgoingResult = foundCall.handler.invoke(context)) {
                                is OutgoingCallResponse.Ok -> {
                                    sendResponse(outgoingResult.result, outgoingResult.statusCode.value)
                                }

                                is OutgoingCallResponse.Error -> {
                                    sendResponse(outgoingResult.error, outgoingResult.statusCode.value)
                                }

                                is OutgoingCallResponse.AlreadyDelivered -> {
                                    // Do nothing
                                }
                            }
                        } catch (ex: Throwable) {
                            if (ex is RPCException) {
                                runCatching {
                                    sendResponse(CommonErrorMessage(ex.why, ex.errorCode), ex.httpStatusCode.value)
                                }
                            } else {
                                log.warn(
                                    "Uncaught exception in ws handler for ${foundCall.call}\n" +
                                        ex.stackTraceToString()
                                )

                                runCatching {
                                    sendResponse(CommonErrorMessage("Internal Server Error", null), 500)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
