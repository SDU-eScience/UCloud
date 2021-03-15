package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.Log
import dk.sdu.cloud.service.LogLevel
import dk.sdu.cloud.service.printlnWithLogColor
import h2o.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import kotlinx.cinterop.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import platform.posix.SIZE_MAX
import platform.posix.strdup
import platform.posix.free as posixFree
import uv.*
import uv.uv_handle_s
import uv.uv_loop_t
import uv.uv_stream_t
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen

fun h2o_send_inline(req: CValuesRef<h2o.h2o_req_t>?, body: String) {
    h2o_send_inline(req, body, body.length.toULong())
}

class ThisWillNeverBeFreed(val staticString: String) {
    val nativelyAllocated = strdup(staticString)
    val length = staticString.length.toULong()
}

fun h2o_req_t.addHeader(headerToken: CPointer<h2o_token_t>?, value: ThisWillNeverBeFreed) {
    h2o_add_header_kt(pool.ptr, res.headers.ptr, headerToken, null, value.nativelyAllocated, value.length)
}

object HeaderValues {
    val contentTypeApplicationJson =
        ThisWillNeverBeFreed(ContentType.Application.Json.withCharset(Charsets.UTF_8).toString())
}


class H2OServer {
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

    @OptIn(ExperimentalUnsignedTypes::class)
    fun start() {
        freeze()
        val log = Log("Server")
        h2oServer.value = this

        val scope = Arena()

        val ctx = scope.alloc<h2o_context_t>()
        val config = scope.alloc<h2o_globalconf_t>()
        h2o_config_init(config.ptr)
        config.max_request_entity_size = (1024 * 1024 * 64L).toULong()

        val defaultPin = "default".pin()
        val hostConfig = h2o_config_register_host(
            config.ptr,
            h2o_iovec_init(defaultPin.addressOf(0), defaultPin.get().length.toULong()),
            65535
        )

        // Register routes
        val pathConfig = h2o_config_register_path(hostConfig, "/", 0)
        // TODO What the fuck is the size argument here?
        val handler = h2o_create_handler(pathConfig, sizeOf<h2o_handler_t>().toULong())
            ?: error("h2o_create_handler returned null")
        handler.pointed.on_req = staticCFunction { self: CPointer<h2o_handler_t>?, reqPtr: CPointer<h2o_req_t>? ->
            if (self == null || reqPtr == null) return@staticCFunction -1
            val log = Log("H2OHttpHandler")

            val req = reqPtr.pointed
            val path = req.path_normalized.base!!.readBytes(req.path_normalized.len.toInt()).decodeToString()
            val query = if (req.query_at != SIZE_MAX) {
                val queryAt = req.query_at.toInt()
                (req.path.base!! + queryAt)!!.readBytes(req.path.len.toInt() - queryAt).decodeToString()
                    .removePrefix("?")
            } else {
                null
            }

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
                foundCall = callWithHandler as CallWithHandler<Any, Any, Any>
                break
            }

            if (foundCall == null) {
                h2o_send_error_404(reqPtr, "Not found", "Not found", 0)
                return@staticCFunction 0
            }

            try {
                val (call, handler) = foundCall
                val http = call.http

                val requestMessage = try {
                    if (call.requestType == Unit.serializer()) {
                        Unit
                    } else if (http.body is HttpBody.BoundToEntireRequest<*>) {
                        if (req.entity.len <= 0UL) {
                            h2o_send_error_400(reqPtr, "Bad Request", "Missing request body", 0)
                            return@staticCFunction 0
                        }

                        val bytes = req.entity.base!!.readBytes(req.entity.len.toInt())
                        val entireMessage = bytes.decodeToString(throwOnInvalidSequence = true)
                        defaultMapper.decodeFromString(call.requestType, entireMessage)
                    } else if (http.params?.parameters?.isNotEmpty() == true) {
                        if (query == null) throw SerializationException("Missing query parameters")
                        ParamsParsing(query, call).decodeSerializableValue(call.requestType)
                    } else {
                        error("Unable to parse request")
                    }
                } catch (ex: Throwable) {
                    h2o_send_error_400(reqPtr, "Bad request", "Invalid request body", 0)
                    return@staticCFunction 0
                }

                //log.info("Incoming call: ${call.fullName} [$requestMessage]")
                val context = CallHandler(IngoingCall(AttributeContainer(), H2OCtx(reqPtr)), requestMessage, call)

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
            } catch (ex : Throwable) {
                if (ex is RPCException) {
                    req.res.status = ex.httpStatusCode.value
                    req.addHeader(H2O_TOKEN_CONTENT_TYPE, HeaderValues.contentTypeApplicationJson)
                    h2o_send_inline(reqPtr, defaultMapper.encodeToString(CommonErrorMessage(ex.why, ex.errorCode)))
                } else {
                    log.warn(ex.stackTraceToString())
                    h2o_send_error_500(reqPtr, "Internal Server Error", "Internal Server Error", 0)
                }
            }

            0
        }
        // End of routes

        val loop = scope.alloc<uv_loop_t>()
        uv_loop_init(loop.ptr)
        h2o_context_init(ctx.ptr, loop.ptr.reinterpret(), config.ptr)
        //val acceptCtx = scope.alloc<h2o_accept_ctx_t>()
        accept_ctx.ctx = ctx.ptr
        accept_ctx.hosts = config.hosts

        val listener = scope.alloc<uv_tcp_t>()
        val addr = scope.alloc<uv.sockaddr_in>()
        uv_tcp_init(ctx.loop?.reinterpret(), listener.ptr)
        uv_ip4_addr("127.0.0.1", 8889, addr.ptr)
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

        printlnWithLogColor(LogLevel.INFO, """
             __  __  ____    ___                      __     
            /\ \/\ \/\  _`\ /\_ \                    /\ \    
            \ \ \ \ \ \ \/\_\//\ \     ___   __  __  \_\ \      Version 2021.1.0
             \ \ \ \ \ \ \/_/_\ \ \   / __`\/\ \/\ \ /'_` \     Running on http://localhost:8889
              \ \ \_\ \ \ \L\ \\_\ \_/\ \L\ \ \ \_\ /\ \L\ \ 
               \ \_____\ \____//\____\ \____/\ \____\ \___,_\
                \/_____/\/___/ \/____/\/___/  \/___/ \/__,_ /
                                                 
        """.trimIndent())
        uv_run(ctx.loop?.reinterpret(), UV_RUN_DEFAULT)
    }
}
