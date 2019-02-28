package dk.sdu.cloud.calls.server

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpBody
import dk.sdu.cloud.calls.HttpHeaderParameter
import dk.sdu.cloud.calls.HttpPathSegment
import dk.sdu.cloud.calls.HttpQueryParameter
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.companionInstance
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.httpOrNull
import dk.sdu.cloud.calls.toKtorTemplate
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.content.TextContent
import io.ktor.features.conversionService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.withCharset
import io.ktor.request.header
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.method
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.util.DataConversionException
import io.ktor.util.pipeline.PipelineContext
import kotlinx.io.IOException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

class IngoingHttpInterceptor(
    private val engine: ApplicationEngine,
    private val rpcServer: RpcServer
) : IngoingRequestInterceptor<HttpCall, HttpCall.Companion> {
    override val companion: HttpCall.Companion = HttpCall

    override fun onStop() {
        engine.stop(gracePeriod = 0L, timeout = 30L, timeUnit = TimeUnit.SECONDS)
    }

    override fun addCallListenerForCall(call: CallDescription<*, *, *>) {
        val httpDescription = call.httpOrNull ?: return

        engine.application.routing {
            route(httpDescription.path.toKtorTemplate(fullyQualified = true)) {
                method(httpDescription.method) {
                    handle {
                        try {
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

    private suspend fun <R : Any> defaultConverterRequestBody(
        ctx: HttpCall,
        typeReference: TypeReference<R>
    ): R? {
        try {
            val receiveOrNull = ctx.call.receiveOrNull<String>()?.takeIf { it.isNotEmpty() } ?: return null
            return defaultMapper.readValue<R>(receiveOrNull, typeReference)
        } catch (ex: MismatchedInputException) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }
    }

    private suspend fun <R : Any> defaultConverterRequestBody(
        ctx: HttpCall,
        typeReference: Class<R>
    ): R? {
        try {
            val receiveOrNull = ctx.call.receiveOrNull<String>()?.takeIf { it.isNotEmpty() } ?: return null
            return defaultMapper.readValue<R>(receiveOrNull, typeReference)
        } catch (ex: MismatchedInputException) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }
    }

    private fun defaultStringConverter(ctx: HttpCall, name: String, value: String, returnType: Type): Any? {
        return try {
            ctx.call.application.conversionService.fromValues(listOf(value), returnType)
        } catch (ex: DataConversionException) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Bad value for parameter '$name'")
        } catch (ex: NoSuchElementException) {
            // For some reason this exception is (incorrectly?) thrown if conversion fails for enums
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Bad value for parameter '$name'")
        }
    }

    override suspend fun <R : Any> parseRequest(ctx: HttpCall, call: CallDescription<R, *, *>): R {
        val http = call.http

        if (call.requestType.type == Unit::class.java) {
            @Suppress("UNCHECKED_CAST")
            return Unit as R
        }

        if (http.body is HttpBody.BoundToEntireRequest<*>) {
            val companionInstance = call.requestType.companionInstance

            @Suppress("UNCHECKED_CAST")
            return when {
                http.body.ingoingConverter != null -> http.body.ingoingConverter.invoke(ctx.call)
                companionInstance is HttpServerConverter.IngoingBody<*> ->
                    companionInstance.serverIngoingBody(call, ctx)
                else -> defaultConverterRequestBody(ctx, call.requestType)
            } as? R ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }

        // We need to start going through all properties and parse them

        val mappedQueryParameters = http
            .params
            ?.parameters
            ?.mapNotNull { it.bindValuesFromCall(ctx, call) }
            ?.toMap() ?: emptyMap()


        val mappedPathParameters = http
            .path
            .segments
            .mapNotNull { it.bindValuesFromCall(ctx, call) }
            .toMap()

        val mappedHeaders = http
            .headers
            ?.parameters
            ?.mapNotNull { it.bindValuesFromCall(ctx, call) }
            ?.toMap() ?: emptyMap()

        val mappedBody = when (val body = http.body) {
            null -> null
            is HttpBody.BoundToEntireRequest<*> -> throw IllegalStateException("Assertion error")
            is HttpBody.BoundToSubProperty -> {
                val property = body.property
                @Suppress("UNCHECKED_CAST")
                val type = property.returnType.javaType as Class<Any>
                val name = property.name
                val companionInstance = type.kotlin.companionObjectInstance

                @Suppress("UNCHECKED_CAST")
                val converted = when {
                    body.ingoingConverter != null -> body.ingoingConverter.invoke(ctx.call)
                    companionInstance is HttpServerConverter.IngoingBody<*> -> companionInstance.serverIngoingBody(
                        call,
                        ctx
                    )
                    else -> defaultConverterRequestBody(ctx, type)
                } as? R?

                if (converted == null && !property.returnType.isMarkedNullable) {
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Missing parameter for '$name'")
                }

                mapOf(name to converted)
            }
        } ?: emptyMap()

        val values = mappedQueryParameters + mappedPathParameters + mappedHeaders + mappedBody

        return RequestParsing.constructFromAttributes(call.requestType.type, values)
    }

    private fun <R : Any> HttpHeaderParameter<R>.bindValuesFromCall(
        ctx: HttpCall,
        call: CallDescription<R, *, *>
    ): Pair<String, Any?>? {
        return when (this) {
            is HttpHeaderParameter.Simple<R> -> {
                val hasRequiredHeader = ctx.call.request.headers.contains(header, value)
                if (!hasRequiredHeader) {
                    log.debug("Missing header value: '$header' -> '$value'")
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }
                null
            }

            is HttpHeaderParameter.Property<R, *> -> {
                val returnType = property.returnType
                val value = ctx.call.request.header(header)
                val companionInstance = returnType.jvmErasure.companionObjectInstance

                val converted = when {
                    value == null -> null
                    ingoingConverter != null -> ingoingConverter.invoke(value)
                    companionInstance is HttpServerConverter.IngoingHeader<*> -> {
                        companionInstance.serverIngoingHeader(call, ctx, header, value)
                    }
                    else -> defaultStringConverter(ctx, property.name, value, returnType.javaType)
                }

                Pair(property.name, converted)
            }

            is HttpHeaderParameter.Present -> {
                val hasRequiredHeader = ctx.call.request.headers.contains(header)
                if (!hasRequiredHeader) {
                    log.debug("Missing header '$header'")
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }
                null
            }
        }
    }

    private fun <R : Any> HttpQueryParameter<R>.bindValuesFromCall(
        ctx: HttpCall,
        call: CallDescription<*, *, *>
    ): Pair<String, Any?>? {
        return when (this) {
            is HttpQueryParameter.Property<R, *> -> {
                val returnType = property.returnType
                val name = property.name
                val value = ctx.call.request.queryParameters[name]
                val companionInstance = returnType.jvmErasure.companionObjectInstance

                val converted = when {
                    value == null -> null
                    ingoingConverter != null -> ingoingConverter.invoke(name, value)
                    companionInstance is HttpServerConverter.IngoingQuery<*> -> {
                        companionInstance.serverIngoingQuery(call, ctx, name, value)
                    }
                    else -> defaultStringConverter(ctx, name, value, returnType.javaType)
                }

                if (converted == null && !returnType.isMarkedNullable) {
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Missing parameter for '$name'")
                }

                Pair(name, converted)
            }
        }
    }

    private fun <R : Any> HttpPathSegment<R>.bindValuesFromCall(
        ctx: HttpCall,
        call: CallDescription<*, *, *>
    ): Pair<String, Any?>? {
        return when (this) {
            is HttpPathSegment.Property<R, *> -> {
                val returnType = property.returnType
                val name = property.name
                val value = ctx.call.parameters[name]
                val companionInstance = returnType.jvmErasure.companionObjectInstance

                val converted = when {
                    value == null -> null
                    ingoingConverter != null -> ingoingConverter.invoke(value)
                    companionInstance is HttpServerConverter.IngoingPath<*> ->
                        companionInstance.serverIngoingPath(call, ctx, value)
                    else -> defaultStringConverter(ctx, name, value, returnType.javaType)
                }

                if (converted == null && !returnType.isMarkedNullable) {
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Missing parameter for '$name'")
                }

                Pair(name, converted)
            }

            else -> null
        }
    }

    override suspend fun <R : Any, S : Any, E : Any> produceResponse(
        ctx: HttpCall,
        call: CallDescription<R, S, E>,
        callResult: OutgoingCallResponse<S, E>
    ) {
        log.debug("Producing response: $ctx, $call, $callResult")
        val (type, responseItem) = when (callResult) {
            is OutgoingCallResponse.Ok -> Pair(call.successType, callResult.result)
            is OutgoingCallResponse.Error -> Pair(call.errorType, callResult.error)
            is OutgoingCallResponse.AlreadyDelivered -> return
        }

        if (responseItem != null) {
            ctx.call.response.status(callResult.statusCode)
            if (responseItem is HttpServerConverter.OutgoingBody) {
                ctx.call.respond(responseItem.serverOutgoingBody(call, ctx))
            } else {
                ctx.call.respond(
                    TextContent(
                        defaultMapper.writerFor(type).writeValueAsString(responseItem),
                        ContentType.Application.Json.withCharset(Charsets.UTF_8)
                    )
                )
            }
        } else {
            ctx.call.respond(callResult.statusCode)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

object HttpServerConverter {
    interface IngoingPath<T : Any> {
        fun serverIngoingPath(
            description: CallDescription<*, *, *>,
            call: HttpCall,
            value: String
        ): T
    }

    interface IngoingQuery<T : Any> {
        fun serverIngoingQuery(
            description: CallDescription<*, *, *>,
            call: HttpCall,
            name: String,
            value: String
        ): T
    }

    interface IngoingBody<T : Any> {
        suspend fun serverIngoingBody(
            description: CallDescription<*, *, *>,
            call: HttpCall
        ): T
    }

    interface IngoingHeader<T : Any> {
        fun serverIngoingHeader(
            description: CallDescription<*, *, *>,
            call: HttpCall,
            header: String,
            value: String
        ): T
    }

    interface OutgoingBody {
        fun serverOutgoingBody(description: CallDescription<*, *, *>, call: HttpCall): OutgoingContent
    }
}
