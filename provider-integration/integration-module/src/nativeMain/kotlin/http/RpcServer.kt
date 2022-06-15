package dk.sdu.cloud.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.debug.DebugContext
import dk.sdu.cloud.debug.DebugCoroutineContext
import dk.sdu.cloud.debug.DebugMessage
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.detail
import dk.sdu.cloud.debug.everythingD
import dk.sdu.cloud.debug.normalD
import dk.sdu.cloud.debugSystem
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.logThrowable
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ConnectionData(
    val connectionId: Int
)

const val version = "2022.1.0"

data class HttpContext(
    val path: String,
    val payload: ByteBuffer,
    val headers: List<Header>,
    val session: HttpClientSession<ConnectionData>,
    val cors: CorsSettings?,
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
    private val cors: CorsSettings? = null,
) {
    private val handlerBuilder = ArrayList<CallWithHandler<*, *, *>>()
    val handlers: List<CallWithHandler<*, *, *>>
        get() = handlerBuilder
    private val _isReady = atomic(false)
    val isReady: Boolean get() = _isReady.value

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
                append(welcomeMessage)
                append("\u001B[0m")
            })
        }

        val idGenerator = atomic(0)
        val contentTypeJson = listOf(Header("Content-Type", "application/json"))
        startServer(
            port,
            { ConnectionData(idGenerator.getAndIncrement()) },
            isReadyHandle = _isReady,
            cors = cors,
            httpRequestHandler = object : HttpRequestHandler<ConnectionData> {
                override fun HttpClientSession<ConnectionData>.handleRequest(
                    method: HttpMethod,
                    path: String,
                    headers: List<Header>,
                    payload: ByteBuffer
                ) {
                    runBlocking {
                        val start = Time.now()
                        val debugCtx = DebugContext.create()
                        withContext(DebugCoroutineContext(debugCtx)) {
                            debugSystem.everythingD(
                                "Incoming HTTP request '$path'",
                                JsonObject(
                                    mapOf(
                                        "method" to JsonPrimitive(method.value),
                                        "path" to JsonPrimitive(path),
                                        "payloadSize" to JsonPrimitive(payload.readerRemaining()),
                                        "headers" to JsonObject(
                                            headers.associate { it.header to JsonPrimitive(it.value) }
                                        )
                                    )
                                ),
                                debugCtx
                            )

                            val origin = headers.find { it.header.equals("origin", ignoreCase = true) }?.value
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
                                log.debug("$method $path 404 Not Found")
                                debugSystem?.sendMessage(
                                    DebugMessage.ServerResponse(
                                        DebugContext.create(),
                                        Time.now(),
                                        null,
                                        MessageImportance.THIS_IS_NORMAL,
                                        null,
                                        null,
                                        404,
                                        Time.now() - start
                                    )
                                )
                                sendHttpResponse(404, defaultHeaders(origin = origin, cors = cors))
                                return@withContext
                            }

                            val (call, handler) = foundCall
                            try {
                                val http = call.http
                                val requestMessage = try {
                                    when {
                                        call.requestType == Unit.serializer() -> {
                                            @Suppress("RedundantUnitExpression")
                                            Unit
                                        }

                                        http.body is HttpBody.BoundToEntireRequest<*> -> {
                                            if (payload.readerRemaining() <= 0) {
                                                sendHttpResponse(400, defaultHeaders(origin = origin, cors = cors))
                                                return@withContext
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
                                    log.debug(
                                        "Failed to parse request message.\n${
                                            ex.stackTraceToString().prependIndent("  ")
                                        }"
                                    )
                                    debugSystem.logThrowable(
                                        "Failed to parse request message",
                                        ex,
                                        MessageImportance.THIS_IS_NORMAL
                                    )
                                    sendHttpResponse(400, defaultHeaders(origin = origin, cors = cors))
                                    return@withContext
                                }

                                log.debug("Incoming call: ${call.fullName}")
                                val context = CallHandler(
                                    IngoingCall(
                                        AttributeContainer(),
                                        HttpContext(path, payload, headers, this@handleRequest, cors)
                                    ),
                                    requestMessage,
                                    call
                                )
                                middlewares.forEach { it.beforeRequest(context) }
                                val outgoingResult = handler.invoke(context)
                                when (outgoingResult) {
                                    is OutgoingCallResponse.Ok -> {
                                        val encodedPayload = defaultMapper
                                            .encodeToString(call.successType, outgoingResult.result)
                                            .encodeToByteArray()

                                        sendHttpResponseWithData(
                                            200,
                                            contentTypeJson + (cors?.headers(origin) ?: emptyList()),
                                            encodedPayload,
                                        )
                                    }

                                    is OutgoingCallResponse.Error -> {
                                        val error = outgoingResult.error
                                        val encodedPayload = (if (error != null) {
                                            defaultMapper.encodeToString(call.errorType, outgoingResult.error)
                                        } else {
                                            "{}"
                                        }).encodeToByteArray()

                                        log.debug("Responding to: ${call.fullName} ${outgoingResult.statusCode.value}")

                                        sendHttpResponseWithData(
                                            outgoingResult.statusCode.value,
                                            contentTypeJson + (cors?.headers(origin) ?: emptyList()),
                                            encodedPayload
                                        )
                                    }

                                    is OutgoingCallResponse.AlreadyDelivered -> {
                                        // Do nothing
                                    }
                                }

                                middlewares.forEach {
                                    it.afterResponse(
                                        context,
                                        when (outgoingResult) {
                                            is OutgoingCallResponse.AlreadyDelivered -> null
                                            is OutgoingCallResponse.Error -> outgoingResult.error
                                            is OutgoingCallResponse.Ok -> outgoingResult.result
                                        },
                                        outgoingResult.statusCode,
                                        Time.now() - start
                                    )
                                }
                            } catch (ex: Throwable) {
                                if (ex is RPCException) {
                                    if (ex.httpStatusCode.value in 500..599) {
                                        debugSystem.logThrowable("Internal error", ex, MessageImportance.THIS_IS_WRONG)
                                        log.warn(ex.stackTraceToString())
                                    }

                                    debugSystem?.sendMessage(
                                        DebugMessage.ServerResponse(
                                            DebugContext.create(),
                                            Time.now(),
                                            null,
                                            MessageImportance.THIS_IS_ODD,
                                            call.fullName,
                                            JsonObject(mapOf(
                                                "why" to JsonPrimitive(ex.why),
                                                "errorCode" to JsonPrimitive(ex.errorCode)
                                            )),
                                            ex.httpStatusCode.value,
                                            Time.now() - start
                                        )
                                    )

                                    sendHttpResponseWithData(
                                        ex.httpStatusCode.value,
                                        contentTypeJson + (cors?.headers(origin) ?: emptyList()),
                                        defaultMapper
                                            .encodeToString(CommonErrorMessage(ex.why, ex.errorCode))
                                            .encodeToByteArray()
                                    )
                                } else {
                                    log.warn(
                                        "Caught an unexpected error in ${foundCall.call.fullName}\n" +
                                                ex.stackTraceToString()
                                    )

                                    debugSystem.logThrowable("Internal error", ex, MessageImportance.THIS_IS_WRONG)

                                    debugSystem?.sendMessage(
                                        DebugMessage.ServerResponse(
                                            DebugContext.create(),
                                            Time.now(),
                                            null,
                                            MessageImportance.THIS_IS_WRONG,
                                            call.fullName,
                                            null,
                                            500,
                                            Time.now() - start
                                        )
                                    )

                                    sendHttpResponse(500, defaultHeaders(origin = origin, cors = cors))
                                }
                            }
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
                        threadLocalBuffer1.clear()
                        threadLocalBuffer1.put(
                            defaultMapper
                                .encodeToString(WSMessage.Response(request.streamId, Unit, 404))
                                .encodeToByteArray()
                        )
                        sendWebsocketFrame(WebSocketOpCode.TEXT, threadLocalBuffer1)
                        log.debug("Call not found")
                        return
                    }

                    @Suppress("UNCHECKED_CAST")
                    val call = foundCall.call

                    val parsedRequest = runCatching {
                        defaultMapper.decodeFromJsonElement(call.requestType, request.payload)
                    }.getOrNull() ?: run {
                        threadLocalBuffer1.clear()
                        threadLocalBuffer1.put(
                            defaultMapper
                                .encodeToString(WSMessage.Response(request.streamId, Unit, 400))
                                .encodeToByteArray()
                        )
                        sendWebsocketFrame(WebSocketOpCode.TEXT, threadLocalBuffer1)
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

                    log.debug("Incoming call: ${foundCall.call.fullName}")
                    val context = CallHandler(IngoingCall(AttributeContainer(), wsContext), parsedRequest, call)

                    runBlocking {
                        withContext(DebugCoroutineContext(DebugContext.create())) {
                            val start = Time.now()
                            middlewares.forEach { it.beforeRequest(context) }
                            var response: Any? = null
                            var statusCode: Int? = null
                            try {
                                when (val outgoingResult = foundCall.handler.invoke(context)) {
                                    is OutgoingCallResponse.Ok -> {
                                        response = outgoingResult.result
                                        statusCode = outgoingResult.statusCode.value
                                    }

                                    is OutgoingCallResponse.Error -> {
                                        response = outgoingResult.error
                                        statusCode = outgoingResult.statusCode.value
                                    }

                                    is OutgoingCallResponse.AlreadyDelivered -> {
                                        // Do nothing
                                    }
                                }

                                if (statusCode != null) {
                                    sendResponse(response, statusCode)
                                }

                                Unit
                            } catch (ex: Throwable) {
                                if (ex is RPCException) {
                                    response = CommonErrorMessage(ex.why, ex.errorCode)
                                    statusCode = ex.httpStatusCode.value
                                } else {
                                    log.warn(
                                        "Uncaught exception in ws handler for ${foundCall.call}\n" +
                                                ex.stackTraceToString()
                                    )

                                    response = CommonErrorMessage("Internal Server Error", null)
                                    statusCode = 500
                                }

                                runCatching {
                                    sendResponse(response, statusCode ?: 500)
                                }
                            }

                            val responseTime = Time.now() - start
                            middlewares.forEach {
                                it.afterResponse(
                                    context,
                                    response,
                                    HttpStatusCode.parse(statusCode ?: 200),
                                    responseTime
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    private val welcomeMessage = """
         __  __  ____    ___                      __     
        /\ \/\ \/\  _`\ /\_ \                    /\ \    
        \ \ \ \ \ \ \/\_\//\ \     ___   __  __  \_\ \      Version $version
         \ \ \ \ \ \ \/_/_\ \ \   / __`\/\ \/\ \ /'_` \     Running on http://localhost:$port
          \ \ \_\ \ \ \L\ \\_\ \_/\ \L\ \ \ \_\ /\ \L\ \ 
           \ \_____\ \____//\____\ \____/\ \____\ \___,_\
            \/_____/\/___/ \/____/\/___/  \/___/ \/__,_ /
                                             
    """.trimIndent()


    companion object : Loggable {
        override val log = logger()
    }
}
