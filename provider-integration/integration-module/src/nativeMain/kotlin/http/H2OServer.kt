package dk.sdu.cloud.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.utils.DynamicWorkerPool
import h2o.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import platform.posix.*
import platform.posix.free as posixFree
import uv.*
import uv.uv_handle_s
import uv.uv_loop_t
import uv.uv_stream_t
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen
import kotlin.native.internal.NativePtr

@Suppress("FunctionName")
fun h2o_send_inline(req: CValuesRef<h2o_req_t>?, body: String) {
    h2o_send_inline(req, body, body.length.toULong())
}

class ThisWillNeverBeFreed(staticString: String) {
    val nativelyAllocated = strdup(staticString)
    val length = staticString.length.toULong()
}

fun h2o_req_t.addHeader(headerToken: CPointer<h2o_token_t>?, value: ThisWillNeverBeFreed) {
    h2o_add_header_kt(pool.ptr, res.headers.ptr, headerToken, null, value.nativelyAllocated, value.length)
}

object HeaderValues {
    val contentTypeApplicationJson =
        ThisWillNeverBeFreed(ContentType.Application.Json.withCharset(Charsets.UTF_8).toString())
    val contentTypeTextHtml =
        ThisWillNeverBeFreed(ContentType.Text.Html.withCharset(Charsets.UTF_8).toString())
}

object WSFrames {
    const val TEXT_FRAME: UByte = 0x1u
    @Suppress("UNUSED")
    const val BINARY_FRAME: UByte = 0x2u
}

data class OutgoingFrame(val wsId: Int, val connPtr: CPointer<h2o_websocket_conn_t>?, val message: String)

@SharedImmutable
private val outgoingFrames = Channel<OutgoingFrame>(Channel.UNLIMITED)

private const val WS_ID_OFFSET = 1000
private const val MAX_WS_SOCKETS = 1024 * 64

@SharedImmutable
private val webSocketIsOpen = atomicArrayOfNulls<Unit>(MAX_WS_SOCKETS).freeze()

@SharedImmutable
private val workers = DynamicWorkerPool("WS Workers")

@ThreadLocal
private val log = Log("H2OServer")

private fun closeWebSocketConnection(connPtr: CPointer<h2o_websocket_conn_t>) {
    val conn = connPtr.pointed
    val wsId = conn.data?.rawValue?.toLong()?.toInt()?. let { it - WS_ID_OFFSET }
    if (wsId != null) {
        webSocketIsOpen[wsId % MAX_WS_SOCKETS].getAndSet(null)
    }
    h2o_websocket_close(connPtr)
    return
}

data class WebSocketContext<R : Any, S : Any, E : Any>(
    val rawRequest: WSRequest<JsonObject>,
    val call: CallDescription<R, S, E>,
    val sendMessage: (S) -> Unit,
    val sendResponse: (response: S, statusCode: Int) -> Unit,
    val isOpen: () -> Boolean,
)

private data class InternalWsContext(val wsId: Int, val connPtr: CPointer<h2o_websocket_conn_t>, val msgString: String)

private fun onWebSocketMessage(
    connPtr: CPointer<h2o_websocket_conn_t>?,
    argPtr: CPointer<wslay_event_on_msg_recv_arg>?,
) {
    if (connPtr == null) return

    val ourConn = connPtr.pointed
    if (ourConn.data == null) {
        var wsId: Int? = null
        for (i in 0 until MAX_WS_SOCKETS) {
            val success = webSocketIsOpen[i].compareAndSet(null, Unit)
            if (success) {
                wsId = i
                break
            }
        }

        if (wsId == null) {
            log.info("Unable to open more sockets. Maximum connection has been reached!")
            closeWebSocketConnection(connPtr)
            return
        }

        ourConn.data = interpretCPointer(NativePtr.NULL.plus(wsId.toLong() + WS_ID_OFFSET))
    }

    if (argPtr == null) {
        closeWebSocketConnection(connPtr)
        return
    }

    // NOTE(Dan): We need to read the request before passing it to the new thread to make sure that we read the memory
    // while it is actually valid
    val arg = argPtr.pointed
    val conn = connPtr.pointed

    val isControlFrame = ((arg.opcode.toInt() shr 3) and 1) != 0
    if (isControlFrame) return
    val wsId = conn.data?.rawValue?.toLong()?.toInt()?. let { it - WS_ID_OFFSET } ?: run {
        log.debug("Found no ws id")
        return
    }

    val msg = arg.msg
    if (arg.msg_length >= MAX_MESSAGE_SIZE || msg == null) {
        closeWebSocketConnection(connPtr)
        log.debug("Message is too long")
        return
    }
    val msgString = try {
        msg.readBytes(arg.msg_length.toInt()).decodeToString()
    } catch (ex: Throwable) {
        log.info("Invalid byte sequence in text frame, closing connection")
        closeWebSocketConnection(connPtr)
        return
    }

    workers
        .execute(
            { InternalWsContext(wsId, connPtr, msgString).freeze() },
            { (wsId, connPtr, msgString) ->
                runBlocking {
                    val server = h2oServer.value ?: error("not ready yet")

                    val request = runCatching {
                        defaultMapper.decodeFromString<WSRequest<JsonObject>>(msgString)
                    }.getOrNull() ?: run {
                        log.debug("Invalid request $msgString")
                        return@runBlocking
                    }

                    val allCalls = server.handlers
                    @Suppress("UNCHECKED_CAST")
                    val foundCall = allCalls
                        .find { it.call.websocketOrNull != null && it.call.fullName == request.call }
                        as CallWithHandler<Any, Any, Any>?

                    if (foundCall == null) {
                        outgoingFrames.send(
                            OutgoingFrame(
                                wsId,
                                connPtr,
                                defaultMapper.encodeToString(WSMessage.Response(request.streamId, Unit, 404))
                            )
                        )
                        log.debug("Call not found")
                        return@runBlocking
                    }

                    @Suppress("UNCHECKED_CAST")
                    val call = foundCall.call

                    val parsedRequest = runCatching {
                        defaultMapper.decodeFromJsonElement(call.requestType, request.payload)
                    }.getOrNull() ?: run {
                        outgoingFrames.send(
                            OutgoingFrame(
                                wsId,
                                connPtr,
                                defaultMapper.encodeToString(WSMessage.Response(request.streamId, Unit, 400))
                            )
                        )
                        log.debug("Invalid request message")
                        return@runBlocking
                    }

                    val isOpen: () -> Boolean = {
                        webSocketIsOpen[wsId].compareAndSet(Unit, Unit)
                    }

                    val sendMessage: (Any) -> Unit = {
                        if (!isOpen()) {
                            throw RPCException(
                                "Connection already closed",
                                HttpStatusCode(499, "Connection already closed")
                            )
                        }

                        runBlocking {
                            outgoingFrames.send(
                                OutgoingFrame(
                                    wsId,
                                    connPtr,
                                    defaultMapper.encodeToString(
                                        WSMessage.Message.serializer(call.successType),
                                        WSMessage.Message(request.streamId, it)
                                    )
                                )
                            )
                        }
                    }

                    val sendResponse: (Any?, Int) -> Unit = { message, statusCode ->
                        if (!isOpen()) {
                            log.debug("Connection is no longer open")
                            throw RPCException(
                                "Connection already closed",
                                HttpStatusCode(499, "Connection already closed")
                            )
                        }

                        runBlocking {
                            outgoingFrames.send(
                                OutgoingFrame(
                                    wsId,
                                    connPtr,
                                    defaultMapper.encodeToString(
                                        WSMessage.Response.serializer(call.successType),
                                        WSMessage.Response(request.streamId, message ?: Unit, statusCode)
                                    )
                                )
                            )
                        }
                    }

                    val wsContext = WebSocketContext(request, foundCall.call, sendMessage, sendResponse, isOpen)

                    log.debug("Incoming call: ${foundCall.call.fullName} (${request.toString().take(240)})")
                    val context = CallHandler(IngoingCall(AttributeContainer(), wsContext), parsedRequest, call)
                    middlewares.value.forEach { it.beforeRequest(context) }

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
                            log.warn("Uncaught exception in ws handler for ${foundCall.call}\n" +
                                ex.stackTraceToString())

                            runCatching {
                                sendResponse(CommonErrorMessage("Internal Server Error", null), 500)
                            }
                        }
                    }
                }
            }
        )
}

private fun handleHttpRequest(
    self: CPointer<h2o_handler_t>?,
    reqPtr: CPointer<h2o_req_t>?,
): Int {
    if (self == null || reqPtr == null) return -1

    val isWebSocket = h2o_upgrade_websocket_if_needed(reqPtr, staticCFunction(::onWebSocketMessage)) != 0
    if (isWebSocket) return 0

    val req = reqPtr.pointed
    val path = req.path_normalized.base!!.readBytes(req.path_normalized.len.toInt()).decodeToString()
    val query = req.readQuery()

    val method = HttpMethod.parse(req.method.base!!.readBytes(req.method.len.toInt()).decodeToString())

    val server = h2oServer.value ?: error("not ready yet")
    val allCalls = server.handlers
    var foundCall: CallWithHandler<Any, Any, Any>? = null
    for (callWithHandler in allCalls) {
        val (call) = callWithHandler
        if (call.httpOrNull == null) continue
        if (call.http.method != method) {
            continue
        }

        val expectedPath =
            (call.http.path.basePath.removeSuffix("/") + "/" +
                call.http.path.segments.joinToString("/") {
                    when (it) {
                        is HttpPathSegment.Simple -> it.text
                    }
                }).removePrefix("/").removeSuffix("/").let { "/$it" }
        if (path != expectedPath) continue
        @Suppress("UNCHECKED_CAST")
        foundCall = callWithHandler as CallWithHandler<Any, Any, Any>
        break
    }

    if (foundCall == null) {
        h2o_send_error_404(reqPtr, "Not found", "Not found", 0)
        return 0
    }

    try {
        val (call, handler) = foundCall
        val http = call.http

        val requestMessage = try {
            when {
                call.requestType == Unit.serializer() -> {
                    @Suppress("RemoveRedundantUnit")
                    Unit
                }
                http.body is HttpBody.BoundToEntireRequest<*> -> {
                    if (req.entity.len <= 0UL) {
                        h2o_send_error_400(reqPtr, "Bad Request", "Missing request body", 0)
                        return 0
                    }

                    val bytes = req.entity.base!!.readBytes(req.entity.len.toInt())
                    val entireMessage = bytes.decodeToString(throwOnInvalidSequence = true)
                    defaultMapper.decodeFromString(call.requestType, entireMessage)
                }
                http.params?.parameters?.isNotEmpty() == true -> {
                    if (query == null) throw SerializationException("Missing query parameters")
                    ParamsParsing(query, call).decodeSerializableValue(call.requestType)
                }
                else -> {
                    error("Unable to parse request")
                }
            }
        } catch (ex: Throwable) {
            h2o_send_error_400(reqPtr, "Bad request", "Invalid request body", 0)
            return 0
        }

        log.debug("Incoming call: ${call.fullName} [$requestMessage]")
        val context = CallHandler(IngoingCall(AttributeContainer(), HttpContext(reqPtr)), requestMessage, call)

        middlewares.value.forEach { it.beforeRequest(context) }

        when (val outgoingResult = handler.invoke(context)) {
            is OutgoingCallResponse.Ok -> {
                req.addHeader(H2O_TOKEN_CONTENT_TYPE, HeaderValues.contentTypeApplicationJson)
                req.res.status = outgoingResult.statusCode.value
                h2o_send_inline(reqPtr, defaultMapper.encodeToString(call.successType, outgoingResult.result))
            }

            is OutgoingCallResponse.Error -> {
                val error = outgoingResult.error
                req.addHeader(H2O_TOKEN_CONTENT_TYPE, HeaderValues.contentTypeApplicationJson)
                req.res.status = outgoingResult.statusCode.value
                h2o_send_inline(
                    reqPtr,
                    if (error != null) defaultMapper.encodeToString(call.errorType, outgoingResult.error)
                    else ""
                )
            }

            is OutgoingCallResponse.AlreadyDelivered -> {
                // Do nothing
            }
        }
    } catch (ex: Throwable) {
        if (ex is RPCException) {
            req.res.status = ex.httpStatusCode.value
            req.addHeader(H2O_TOKEN_CONTENT_TYPE, HeaderValues.contentTypeApplicationJson)
            h2o_send_inline(reqPtr, defaultMapper.encodeToString(CommonErrorMessage(ex.why, ex.errorCode)))
        } else {
            log.warn("Caught an unexpected error in ${foundCall.call.fullName}\n" + ex.stackTraceToString())
            h2o_send_error_500(reqPtr, "Internal Server Error", "Internal Server Error", 0)
        }
    }

    return 0
}

fun h2o_req_t.readQuery(): String? {
    val req = this
    return if (req.query_at != SIZE_MAX) {
        val queryAt = req.query_at.toInt()
        (req.path.base!! + queryAt)!!.readBytes(req.path.len.toInt() - queryAt).decodeToString()
            .removePrefix("?")
    } else {
        null
    }
}

private val MAX_MESSAGE_SIZE = 1024UL * 1024UL * 64UL

@Suppress("RedundantUnitExpression")
class H2OServer(
    private val port: Int,
    private val showWelcomeMessage: Boolean = true,
) {
    private val handlerBuilder = ArrayList<CallWithHandler<*, *, *>>()
    val handlers: List<CallWithHandler<*, *, *>>
        get() = handlerBuilder

    fun <R : Any, S : Any, E : Any> implement(
        call: CallDescription<R, S, E>,
        handler: CallHandler<R, S, E>.() -> OutgoingCallResponse<S, E>,
    ) {
        if (isFrozen) error("implement was called after start()")
        handlerBuilder.add(CallWithHandler(call, handler))
    }

    fun start() {
        freeze()
        h2oServer.value = this

        val scope = Arena()

        val ctx = scope.alloc<h2o_context_t>()
        val config = scope.alloc<h2o_globalconf_t>()
        h2o_config_init(config.ptr)
        config.max_request_entity_size = MAX_MESSAGE_SIZE

        val defaultPin = "default".pin()
        val hostConfig = h2o_config_register_host(
            config.ptr,
            h2o_iovec_init(defaultPin.addressOf(0), defaultPin.get().length.toULong()),
            65535
        )

        val pathConfig = h2o_config_register_path(hostConfig, "/", 0)
        // TODO Not sure what the size argument is
        val handler = h2o_create_handler(pathConfig, sizeOf<h2o_handler_t>().toULong())
            ?: error("h2o_create_handler returned null")
        handler.pointed.on_req = staticCFunction { self: CPointer<h2o_handler_t>?, reqPtr: CPointer<h2o_req_t>? ->
            handleHttpRequest(self, reqPtr)
        }

        val loop = scope.alloc<uv_loop_t>()
        uv_loop_init(loop.ptr)
        h2o_context_init(ctx.ptr, loop.ptr.reinterpret(), config.ptr)
        // NOTE(Dan): We are using a uv_timer_t in stead of uv_idle since polling on every iteration will cause the CPU
        // to spin to 100% (although only on a single CPU). This introduces up to 1ms of delay for WS messages but it
        // pales in comparison to the delay introduced by the network.
        val idle = scope.alloc<uv.uv_timer_t>()
        uv_timer_init(loop.ptr, idle.ptr)
        uv_timer_start(idle.ptr, staticCFunction { _: CPointer<uv.uv_timer_t>? ->
            // This runs on the same thread as the WS code
            while (true) {
                val frame = outgoingFrames.poll() ?: break
                val connPtr = frame.connPtr ?: continue
                val conn = connPtr.pointed
                val messageToSend = frame.message.encodeToByteArray()

                val wsId = frame.wsId
                if (wsId !in 0 until MAX_WS_SOCKETS) {
                    log.debug("We were asked to send a message to an unknown connection. Discarding this message.")
                    continue
                } else {
                    val isOpen = webSocketIsOpen[wsId % MAX_WS_SOCKETS].compareAndSet(Unit, Unit)
                    if (!isOpen) {
                        log.debug("Connection was closed before we could send a message")
                        continue
                    }
                }

                memScoped {
                    messageToSend.usePinned { pin ->
                        val wsMessage = alloc<wslay_event_msg>()
                        wsMessage.opcode = WSFrames.TEXT_FRAME
                        wsMessage.msg = pin.addressOf(0).reinterpret()
                        wsMessage.msg_length = messageToSend.size.toULong()
                        wslay_event_queue_msg(conn.ws_ctx, wsMessage.ptr)
                        h2o_websocket_proceed(connPtr)
                    }
                }
            }
        }, 1UL, 1UL)
        @Suppress("unused")
        accept_ctx.ctx = ctx.ptr
        accept_ctx.hosts = config.hosts

        val listener = scope.alloc<uv_tcp_t>()
        val addr = scope.alloc<platform.posix.sockaddr_in>()
        uv_tcp_init(ctx.loop?.reinterpret(), listener.ptr)
        uv_ip4_addr("127.0.0.1", port, addr.ptr.reinterpret())
        check(uv_tcp_bind(listener.ptr, addr.ptr.reinterpret(), 0) == 0) { "Could not bind to address" }
        val uvListenResult = uv_listen(
            listener.ptr.reinterpret(),
            128,
            staticCFunction { l: CPointer<uv_stream_t>?, status: Int ->
                if (status != 0 || l == null) return@staticCFunction
                val conn = h2o_mem_alloc(sizeOf<uv_tcp_t>().toULong())?.reinterpret<uv_tcp_t>()
                    ?: error("Could not allocate connection")
                uv_tcp_init(l.pointed.loop, conn)
                if (uv_accept(l, conn.reinterpret()) != 0) {
                    uv_close(
                        conn.reinterpret(),
                        staticCFunction { ptr: CPointer<uv_handle_s>? ->
                            posixFree(ptr)
                        }
                    )
                    return@staticCFunction
                }
                val sock = h2o_uv_socket_create(
                    conn.reinterpret(),
                    staticCFunction { ptr: CPointer<h2o.uv_handle_s>? ->
                        posixFree(ptr)
                    }
                )
                h2o_accept(accept_ctx.ptr, sock)
            }
        )
        check(uvListenResult == 0) { "Could not initialize socket listener" }

        workers.start()

        if (showWelcomeMessage) {
            println(buildString {
                append("\u001B[34m")
                append(
                    """
                         __  __  ____    ___                      __     
                        /\ \/\ \/\  _`\ /\_ \                    /\ \    
                        \ \ \ \ \ \ \/\_\//\ \     ___   __  __  \_\ \      Version 2021.1.0
                         \ \ \ \ \ \ \/_/_\ \ \   / __`\/\ \/\ \ /'_` \     Running on http://localhost:8889
                          \ \ \_\ \ \ \L\ \\_\ \_/\ \L\ \ \ \_\ /\ \L\ \ 
                           \ \_____\ \____//\____\ \____/\ \____\ \___,_\
                            \/_____/\/___/ \/____/\/___/  \/___/ \/__,_ /
                                                             
                    """.trimIndent()
                )
                append("\u001B[0m")
            })
        }
        uv_run(ctx.loop?.reinterpret(), UV_RUN_DEFAULT)
    }
}
