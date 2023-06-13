package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.UCloudMessage
import dk.sdu.cloud.debug.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.logThrowable
import dk.sdu.cloud.messages.BinaryType
import dk.sdu.cloud.messages.BinaryTypeSerializer
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.feature
import dk.sdu.cloud.micro.featureOrNull
import dk.sdu.cloud.service.Loggable
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.TimeUnit

class IngoingHttpInterceptor(
    private val engine: ApplicationEngine,
    private val rpcServer: RpcServer,
    private val micro: Micro,
) : IngoingRequestInterceptor<HttpCall, HttpCall.Companion> {
    override val companion: HttpCall.Companion = HttpCall

    // NOTE(Dan): This needs to be lazy to make sure the debug system has been initialized
    private val debug by lazy { micro.feature(DebugSystemFeature) }

    override fun onStop() {
        engine.stop(gracePeriod = 0L, timeout = 30L, timeUnit = TimeUnit.SECONDS)
    }

    override fun addCallListenerForCall(call: CallDescription<*, *, *>) {
        val httpDescription = call.httpOrNull ?: return

        engine.application.routing {
            // toKtorTemplate performs a plain one-to-one mapping of the http/path block semantics to Ktor routing
            // template

            route(httpDescription.path.toPath(), HttpMethod(httpDescription.method.value)) {
                handle {
                    val ctx = HttpCall(this as PipelineContext<Any, ApplicationCall>)

                    debug.system.useContext(
                        type = DebugContextType.SERVER_REQUEST,
                        initialName = call.fullName,
                        initialImportance = MessageImportance.THIS_IS_NORMAL,
                        block = {
                            try {
                                // Calls the handler provided by 'implement'
                                @Suppress("UNCHECKED_CAST")
                                rpcServer.handleIncomingCall(
                                    this@IngoingHttpInterceptor,
                                    call,
                                    ctx
                                )
                            } catch (ex: IOException) {
                                log.debug("Caught IOException:")
                                log.debug(ex.stackTraceToString())
                                throw RPCException.fromStatusCode(dk.sdu.cloud.calls.HttpStatusCode.BadRequest)
                            }
                        }
                    )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R : Any> parseRequest(ctx: HttpCall, call: CallDescription<R, *, *>): R {
        try {
            val http = call.http

            val requestType = call.requestType
            if (requestType is BinaryTypeSerializer<*>) {
                val canParseUCloudMessages = ctx.call.request.header(HttpHeaders.ContentType)
                    ?.let { ContentType.parse(it) } == ContentType.Application.UCloudMessage

                return if (canParseUCloudMessages) {
                    println("Loading binary")
                    ctx.requestAllocator.load(ctx.ktor.call.request.receiveChannel())
                    requestType.companion.create(ctx.requestAllocator.root()) as R
                } else {
                    println("Loading json")
                    val receiveOrNull = try {
                        ctx.ktor.call.request.receiveChannel().readRemaining().readText().takeIf { it.isNotEmpty() }
                    } catch (ex: Throwable) {
                        null
                    } ?: throw RPCException("Received no JSON body but one was expected", HttpStatusCode.BadRequest)

                    val parsedToJson = defaultMapper.decodeFromString(JsonElement.serializer(), receiveOrNull)

                    requestType.companion.decodeFromJson(ctx.requestAllocator, parsedToJson) as R
                }
            } else {
                when {
                    call.requestType == Unit.serializer() -> {
                        return Unit as R
                    }

                    http.body is HttpBody.BoundToEntireRequest<*> -> {
                        val receiveOrNull = try {
                            ctx.ktor.call.request.receiveChannel().readRemaining().readText().takeIf { it.isNotEmpty() }
                        } catch (ex: Throwable) {
                            null
                        }
                        return (
                                if (receiveOrNull != null) defaultMapper.decodeFromString(
                                    call.requestType,
                                    receiveOrNull
                                )
                                else null
                                ) ?: throw RPCException(
                            "Invalid request received (no body?)",
                            dk.sdu.cloud.calls.HttpStatusCode.BadRequest
                        )
                    }

                    http.params != null -> {
                        return ParamsParsing(ctx.ktor.context, call).decodeSerializableValue(call.requestType)
                    }

                    http.headers != null -> {
                        return HeaderParsing(ctx.ktor.context, call).decodeSerializableValue(call.requestType)
                    }

                    else -> throw RPCException(
                        "Unable to deserialize request. No source of input!",
                        dk.sdu.cloud.calls.HttpStatusCode.InternalServerError
                    )
                }
            }
        } catch (ex: Throwable) {
            when {
                ex.cause is RPCException -> {
                    throw ex.cause!!
                }
                ex is RPCException -> {
                    throw ex
                }
                else -> {
                    log.debug(ex.stackTraceToString())
                    debug.system.logThrowable("Failed to parse request", ex, MessageImportance.IMPLEMENTATION_DETAIL)
                    throw RPCException("Invalid request received (wrong type)", dk.sdu.cloud.calls.HttpStatusCode.BadRequest)
                }
            }
        }
    }

    override suspend fun <R : Any, S : Any, E : Any> produceResponse(
        ctx: HttpCall,
        call: CallDescription<R, S, E>,
        callResult: OutgoingCallResponse<S, E>,
    ) {
        when (callResult) {
            is OutgoingCallResponse.Ok -> {
                produceResponse(ctx, callResult.statusCode, call.successType, callResult.result)
            }

            is OutgoingCallResponse.Error -> {
                produceResponse(ctx, callResult.statusCode, call.errorType, callResult.error)
            }

            is OutgoingCallResponse.AlreadyDelivered -> return
        }
    }

    private suspend fun <T> produceResponse(
        ctx: HttpCall,
        statusCode: io.ktor.http.HttpStatusCode,
        serializer: KSerializer<T>,
        data: T?
    ) {
        if (data == null) {
            ctx.ktor.call.respond(statusCode)
        } else {
            if (serializer is BinaryTypeSerializer) {
                val acceptHeader = ctx.ktor.call.request.header(HttpHeaders.Accept) ?: ""
                val acceptParts = acceptHeader.split(",").map { it.trim() }
                // NOTE(Dan): We are not matching here since most of our clients are currently sending "Accept: */*". We want them
                // to explicitly declare support for this format.
                val canProduceBinary = acceptParts.any { ContentType.Application.UCloudMessage == ContentType.parse(it) }

                if (canProduceBinary) {
                    println("${ctx.call.request.path()} Producing binary")
                    // NOTE(Dan): We are choosing to use the allocator of the BinaryType to allow stuff like repeating
                    // the request back as a response.
                    val binData = data as BinaryType
                    binData.buffer.allocator.updateRoot(data)
                    val slicedBuffer = binData.buffer.allocator.slicedBuffer()
                    ctx.ktor.call.respondBytesWriter(
                        contentType = ContentType.Application.UCloudMessage,
                        status = statusCode,
                        contentLength = slicedBuffer.remaining().toLong(),
                        producer = {
                            writeFully(slicedBuffer)
                        }
                    )
                    return
                }
            }

            println("${ctx.call.request.path()} Producing json")
            ctx.ktor.call.respond(
                TextContent(
                    defaultMapper.encodeToString(serializer, data),
                    ContentType.Application.Json.withCharset(Charsets.UTF_8)
                )
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
