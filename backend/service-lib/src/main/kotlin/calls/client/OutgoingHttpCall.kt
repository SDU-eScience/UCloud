package dk.sdu.cloud.calls.client

import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpBody
import dk.sdu.cloud.calls.HttpHeaderParameter
import dk.sdu.cloud.calls.HttpPathSegment
import dk.sdu.cloud.calls.HttpRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.messages.BinaryAllocator
import dk.sdu.cloud.messages.BinaryType
import dk.sdu.cloud.messages.BinaryTypeSerializer
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.systemName
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.EmptyContent
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlin.random.Random
import kotlin.reflect.KClass

typealias KtorHttpRequestBuilder = io.ktor.client.request.HttpRequestBuilder

class OutgoingHttpCall(
    val debugCall: CallDescription<*, *, *>,
    val builder: KtorHttpRequestBuilder
) : OutgoingCall {
    override val attributes: AttributeContainer = AttributeContainer()
    var response: HttpResponse? = null
        internal set

    override fun toString(): String = "OutgoingHttpCall(${debugCall.fullName})"

    companion object : OutgoingCallCompanion<OutgoingHttpCall> {
        override val klass: KClass<OutgoingHttpCall> = OutgoingHttpCall::class
        override val attributes: AttributeContainer = AttributeContainer()
    }
}

class OutgoingHttpRequestInterceptor : OutgoingRequestInterceptor<OutgoingHttpCall, OutgoingHttpCall.Companion> {
    override val companion: OutgoingHttpCall.Companion = OutgoingHttpCall.Companion

    fun install(
        client: RpcClient,
        targetHostResolver: OutgoingHostResolver,
    ) {
        with(client) {
            attachFilter(OutgoingHostResolverInterceptor(targetHostResolver))
            attachFilter(OutgoingAuthFilter())

            attachRequestInterceptor(this@OutgoingHttpRequestInterceptor)
        }
    }

    override suspend fun <R : Any, S : Any, E : Any> prepareCall(
        call: CallDescription<R, S, E>,
        request: R,
    ): OutgoingHttpCall {
        return OutgoingHttpCall(call, KtorHttpRequestBuilder())
    }

    @OptIn(InternalAPI::class)
    @Suppress("NestedBlockDepth", "BlockingMethodInNonBlockingContext")
    override suspend fun <R : Any, S : Any, E : Any> finalizeCall(
        call: CallDescription<R, S, E>,
        request: R,
        ctx: OutgoingHttpCall,
    ): IngoingCallResponse<S, E> {
        requestsInFlight.labels(call.fullName).inc()
        requestCounter.labels(call.fullName).inc()

        try {
            val callId = Random.nextInt(10000) // A non-unique call ID for logging purposes only
            val start = Time.now()
            val shortRequestMessage = request.toString().take(100)

            with(ctx.builder) {
                val targetHost = ctx.attributes.outgoingTargetHost
                val scheme = targetHost.scheme ?: "http"
                val host = targetHost.host.removeSuffix("/")
                val port = targetHost.port ?: if (scheme == "https") 443 else 80
                val http = call.http

                val endpoint = http.resolveEndpoint(request, call).removePrefix("/").removeSuffix("/")
                val url = "$scheme://$host:$port/$endpoint"

                url(url)
                method = io.ktor.http.HttpMethod(http.method.value)

                val requestType = call.requestType
                if (requestType is BinaryTypeSerializer<*>) {
                    val binRequest = request as BinaryType
                    binRequest.buffer.allocator.updateRoot(binRequest)

                    val requestBuffer = binRequest.buffer.allocator.slicedBuffer()
                    val length = requestBuffer.remaining().toLong()

                    header(HttpHeaders.Accept, ContentType.Application.UCloudMessage.toString())
                    body = object : OutgoingContent.WriteChannelContent() {
                        override val contentType = ContentType.Application.UCloudMessage
                        override val contentLength: Long = length

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeFully(requestBuffer)
                        }
                    }
                } else {
                    if (body == EmptyContent) {
                        // If a beforeHook has attached a body, don't change it
                        body = http.serializeBody(request, call)
                    }
                    http.serializeHeaders(request).forEach { (name, value, encode) ->
                        if (encode) {
                            header(name, base64Encode(value.encodeToByteArray()))
                        } else {
                            header(name, value)
                        }
                    }

                    // OkHttp fix. It requires a body for certain methods, even though it shouldn't.
                    if (body == EmptyContent && method in setOf(
                            HttpMethod.Put,
                            HttpMethod.Delete,
                            HttpMethod.Post,
                            HttpMethod.Patch
                        )
                    ) {
                        body = TextContent("Fix", ContentType.Text.Plain)
                    }
                }

                log.debug("[$callId] -> ${call.fullName}: $shortRequestMessage")
            }

            try {
                log.trace("Sending request")
                val resp = createHttpClient().use { httpClient ->
                    try {
                        httpClient.request(ctx.builder)
                    } catch (ex: Throwable) {
                        if (ex.stackTraceToString().contains("ConnectException") || ex is EOFException) {
                            log.debug("[$callId] ConnectException: ${ex.message}")
                            return IngoingCallResponse.Error(null as E?, HttpStatusCode.BadGateway, ctx)
                        }

                        throw ex
                    }
                }
                log.trace("Received response")

                ctx.response = resp
                val result = parseResponse(ctx, resp, call, callId)
                log.trace("Parsing complete")
                val end = Time.now()

                val responseDebug =
                    "[$callId] name=${call.fullName} status=${result.statusCode.value} time=${end - start}ms"

                log.debug(responseDebug)
                if (result.statusCode.isSuccess()) {
                    requestsSuccessCounter.labels(call.fullName).inc()
                } else {
                    requestsErrorCounter.labels(call.fullName).inc()
                }
                return result
            } catch (ex: Throwable) {
                if (ex::class.qualifiedName?.contains("StreamResetException") == true) {
                    log.info(buildString {
                        appendLine("We are about to crash. We know the following:")
                        appendLine("  - URL: ${ctx.builder.url.buildString()}")
                        appendLine("  - method: ${ctx.builder.method}")
                        appendLine("  - call: ${call.fullName}")
                        appendLine("  - callId: $callId")
                    })
                }

                throw ex
            }
        } catch(ex: Throwable) {
            requestsErrorCounter.labels(call.fullName).inc()
            throw ex
        } finally {
            requestsInFlight.labels(call.fullName).dec()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <S : Any> parseResponseToType(
        resp: HttpResponse,
        type: KSerializer<S>,
    ): S {
        if (type.descriptor.serialName == "kotlin.Unit") return Unit as S

        if (type is BinaryTypeSerializer<*> && resp.contentType() == ContentType.Application.UCloudMessage) {
            val length = resp.contentLength() ?: throw RPCException(
                "Refusing to parse UCloud message without a content length",
                HttpStatusCode.BadGateway
            )

            if (length >= 1024 * 512) {
                throw RPCException(
                    "Refusing to parse a UCloud message of this size ($length)!",
                    HttpStatusCode.BadGateway
                )
            }

            val allocator = BinaryAllocator(length.toInt(), 0, readOnly = true)

            val channel = resp.bodyAsChannel()
            allocator.load(channel)

            return type.companion.create(allocator.root()) as S
        } else {
            val bodyAsString = resp.bodyAsText()
            return try {
                defaultMapper.decodeFromString(type, bodyAsString)
            } catch (ex: SerializationException) {
                throw RuntimeException(
                    "Could not parse response to type!\nRequest:${bodyAsString.prependIndent("  ")}",
                    ex
                )
            }
        }
    }

    private suspend fun <E : Any, R : Any, S : Any> parseResponse(
        ctx: OutgoingHttpCall,
        resp: HttpResponse,
        call: CallDescription<R, S, E>,
        callId: Int,
    ): IngoingCallResponse<S, E> {
        return if (resp.status.isSuccess()) {
            IngoingCallResponse.Ok(
                parseResponseToType(resp, call.successType),
                dk.sdu.cloud.calls.HttpStatusCode(resp.status.value, resp.status.description),
                ctx
            )
        } else {
            IngoingCallResponse.Error(
                runCatching {
                    parseResponseToType(resp, call.errorType)
                }.getOrElse {
                    log.trace("[$callId] Exception while de-serializing unsuccessful message")
                    log.trace(it.stackTraceToString())
                    null
                },
                dk.sdu.cloud.calls.HttpStatusCode(resp.status.value, resp.status.description),
                ctx,
            )
        }
    }

    private fun defaultBodySerialization(
        payload: Any,
        call: CallDescription<*, *, *>,
    ): OutgoingContent {
        @Suppress("UNCHECKED_CAST")
        return TextContent(
            defaultMapper.encodeToString(call.requestType as KSerializer<Any>, payload),
            ContentType.Application.Json.withCharset(Charsets.UTF_8)
        )
    }

    private fun <R : Any> HttpRequest<R, *, *>.serializeBody(
        request: R,
        call: CallDescription<*, *, *>,
    ): OutgoingContent {
        return when (val body = body) {
            is HttpBody.BoundToEntireRequest<*> -> {
                @Suppress("UNCHECKED_CAST")
                body as HttpBody.BoundToEntireRequest<R>

                defaultBodySerialization(request, call)
            }

            null -> EmptyContent
        }
    }

    data class SerializedHeader(val name: String, val value: String, val base64Encode: Boolean)
    private fun <R : Any> HttpRequest<R, *, *>.serializeHeaders(
        request: R,
    ): List<SerializedHeader> {
        if (headers == null) return emptyList()

        return headers.parameters.mapNotNull {
            when (it) {
                is HttpHeaderParameter.Simple -> SerializedHeader(it.header, it.value, true)
                is HttpHeaderParameter.Present -> SerializedHeader(it.header, "true", true)

                is HttpHeaderParameter.Property<R, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    it as HttpHeaderParameter.Property<R, Any?>

                    val value = it.property.get(request) ?: return@mapNotNull null

                    SerializedHeader(it.header, value.toString(), it.base64Encoded)
                }
            }
        }
    }

    private fun <R : Any> HttpRequest<R, *, *>.resolveEndpoint(
        request: R,
        call: CallDescription<R, *, *>,
    ): String {
        val primaryPath = serializePathSegments(request)
        val queryPathMap = serializeQueryParameters(request, call)
        val queryPath = encodeQueryParamsToString(queryPathMap)

        return (path.basePath.removeSuffix("/") + "/" + primaryPath).removeSuffix("/") + queryPath
    }

    private fun <R : Any> HttpRequest<R, *, *>.serializePathSegments(
        request: R,
    ): String {
        return path.segments.asSequence().mapNotNull {
            when (it) {
                is HttpPathSegment.Simple -> it.text
            }
        }.joinToString("/")
    }

    private fun <R : Any> HttpRequest<R, *, *>.serializeQueryParameters(
        request: R,
        call: CallDescription<R, *, *>,
    ): Map<String, List<String>> {
        if (call.http.params == null) return emptyMap()
        return QueryParameterEncoder(call).also {
            it.encodeSerializableValue(call.requestType, request)
        }.builder
    }

    private fun encodeQueryParamsToString(queryPathMap: Map<String, List<String>>): String {
        return queryPathMap
            .flatMap { param ->
                param.value.map { v -> urlEncode(param.key) + "=" + urlEncode(v) }
            }
            .joinToString("&")
            .takeIf { it.isNotEmpty() }
            ?.let { "?$it" } ?: ""
    }

    companion object : Loggable {
        override val log = logger()

        private const val SAMPLE_FREQUENCY = 100

        private val requestCounter = Counter.build()
            .namespace(systemName)
            .subsystem("rpc_client_http")
            .name("requests_started")
            .help("Total number of requests passing through RpcClient with an HTTP backend")
            .labelNames("request_name")
            .register()

        private val requestsSuccessCounter = Counter.build()
            .namespace(systemName)
            .subsystem("rpc_client_http")
            .name("requests_success")
            .help("Total number of requests which has passed through RpcClient successfully with an HTTP backend")
            .labelNames("request_name")
            .register()

        private val requestsErrorCounter = Counter.build()
            .namespace(systemName)
            .subsystem("rpc_client_http")
            .name("requests_error")
            .help("Total number of requests which has passed through RpcClient with a failure with an HTTP backend")
            .labelNames("request_name")
            .register()

        private val requestsInFlight = Gauge.build()
            .namespace(systemName)
            .subsystem("rpc_client_http")
            .name("requests_in_flight")
            .help("Number of requests currently in-flight in the RpcClient with an HTTP backend")
            .labelNames("request_name")
            .register()
    }
}
