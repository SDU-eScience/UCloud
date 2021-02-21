package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpBody
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.httpOrNull
import dk.sdu.cloud.calls.toKtorTemplate
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.request.*
import io.ktor.response.respond
import io.ktor.routing.method
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.pipeline.PipelineContext
import io.ktor.utils.io.errors.*
import kotlinx.serialization.builtins.serializer
import java.util.*
import java.util.concurrent.TimeUnit

class IngoingHttpInterceptor(
    private val engine: ApplicationEngine,
    private val rpcServer: RpcServer,
) : IngoingRequestInterceptor<HttpCall, HttpCall.Companion> {
    override val companion: HttpCall.Companion = HttpCall

    override fun onStop() {
        engine.stop(gracePeriod = 0L, timeout = 30L, timeUnit = TimeUnit.SECONDS)
    }

    override fun addCallListenerForCall(call: CallDescription<*, *, *>) {
        val httpDescription = call.httpOrNull ?: return

        engine.application.routing {
            // toKtorTemplate performs a plain one-to-one mapping of the http/path block semantics to Ktor routing
            // template
            route(httpDescription.path.toKtorTemplate(fullyQualified = true)) {
                method(httpDescription.method) {
                    handle {
                        try {
                            // Calls the handler provided by 'implement'
                            @Suppress("UNCHECKED_CAST")
                            rpcServer.handleIncomingCall(
                                this@IngoingHttpInterceptor,
                                call,
                                HttpCall(this as PipelineContext<Any, ApplicationCall>)
                            )
                        } catch (ex: IOException) {
                            log.debug("Caught IOException:")
                            log.debug(ex.stackTraceToString())
                            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        }
                    }
                }
            }
        }
    }

    override suspend fun <R : Any> parseRequest(ctx: HttpCall, call: CallDescription<R, *, *>): R {
        try {
            val http = call.http

            when {
                call.requestType == Unit.serializer() -> {
                    @Suppress("UNCHECKED_CAST")
                    return Unit as R
                }
                http.body is HttpBody.BoundToEntireRequest<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val receiveOrNull = ctx.call.receiveOrNull<String>()?.takeIf { it.isNotEmpty() }
                    return (
                        if (receiveOrNull != null) defaultMapper.decodeFromString(call.requestType, receiveOrNull)
                        else null
                        ) ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }

                else -> {
                    return ParamsParsing(ctx.context, call).decodeSerializableValue(call.requestType)
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
                    throw RPCException("Bad request", HttpStatusCode.BadRequest)
                }
            }
        }
    }

    override suspend fun <R : Any, S : Any, E : Any> produceResponse(
        ctx: HttpCall,
        call: CallDescription<R, S, E>,
        callResult: OutgoingCallResponse<S, E>,
    ) {
        ctx.call.response.status(callResult.statusCode)

        when (callResult) {
            is OutgoingCallResponse.Ok -> {
                ctx.call.respond(
                    TextContent(
                        defaultMapper.encodeToString(call.successType, callResult.result),
                        ContentType.Application.Json.withCharset(Charsets.UTF_8)
                    )
                )
            }

            is OutgoingCallResponse.Error -> {
                if (callResult.error == null) {
                    ctx.call.respond(callResult.statusCode)
                } else {
                    ctx.call.respond(
                        TextContent(
                            defaultMapper.encodeToString(call.errorType, callResult.error),
                            ContentType.Application.Json.withCharset(Charsets.UTF_8)
                        )
                    )
                }
            }

            is OutgoingCallResponse.AlreadyDelivered -> return
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
