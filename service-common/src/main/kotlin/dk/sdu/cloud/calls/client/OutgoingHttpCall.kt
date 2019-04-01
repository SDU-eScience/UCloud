package dk.sdu.cloud.calls.client

import com.fasterxml.jackson.core.type.TypeReference
import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpBody
import dk.sdu.cloud.calls.HttpHeaderParameter
import dk.sdu.cloud.calls.HttpPathSegment
import dk.sdu.cloud.calls.HttpQueryParameter
import dk.sdu.cloud.calls.HttpRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.kClass
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.call.receive
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.response.HttpResponse
import io.ktor.client.utils.EmptyContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.http.withCharset
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import java.net.ConnectException
import java.net.URLEncoder
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

typealias KtorHttpRequestBuilder = io.ktor.client.request.HttpRequestBuilder

class OutgoingHttpCall(val builder: KtorHttpRequestBuilder) : OutgoingCall {
    override val attributes: AttributeContainer = AttributeContainer()

    companion object : OutgoingCallCompanion<OutgoingHttpCall> {
        override val klass: KClass<OutgoingHttpCall> = OutgoingHttpCall::class
        override val attributes: AttributeContainer = AttributeContainer()
    }
}

class OutgoingHttpRequestInterceptor : OutgoingRequestInterceptor<OutgoingHttpCall, OutgoingHttpCall.Companion> {
    override val companion: OutgoingHttpCall.Companion = OutgoingHttpCall.Companion

    private val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    fun install(
        client: RpcClient,
        targetHostResolver: OutgoingHostResolver
    ) {
        with(client) {
            attachFilter(OutgoingHostResolverInterceptor(targetHostResolver))
            attachFilter(OutgoingAuthFilter())

            attachRequestInterceptor(this@OutgoingHttpRequestInterceptor)
        }
    }

    override suspend fun <R : Any, S : Any, E : Any> prepareCall(
        call: CallDescription<R, S, E>,
        request: R
    ): OutgoingHttpCall {
        return OutgoingHttpCall(KtorHttpRequestBuilder())
    }

    override suspend fun <R : Any, S : Any, E : Any> finalizeCall(
        call: CallDescription<R, S, E>,
        request: R,
        ctx: OutgoingHttpCall
    ): IngoingCallResponse<S, E> {
        val callId = Random.nextInt(10000) // A non unique call ID for logging purposes only
        val start = System.currentTimeMillis()
        val shortRequestMessage = request.toString().take(100)

        var attempts = 0

        while (true) {
            attempts++
            if (attempts == 5) throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)

            with(ctx.builder) {
                val targetHost = ctx.attributes.outgoingTargetHost
                val scheme = targetHost.scheme ?: "http"
                val host = targetHost.host.removeSuffix("/")
                val port = targetHost.port ?: if (scheme == "https") 443 else 80
                val http = call.http

                val endpoint = http.resolveEndpoint(request, call).removePrefix("/")
                val url = "$scheme://$host:$port/$endpoint"

                url(url)
                method = http.method
                body = http.serializeBody(request, call)
                http.serializeHeaders(request, call).forEach { (name, value) ->
                    header(name, value)
                }

                if (request is HttpClientConverter.OutgoingCustomHeaders) {
                    request.clientAddCustomHeaders(call).forEach { (key, value) ->
                        header(key, value)
                    }
                }

                log.debug("[$callId] -> $url: $shortRequestMessage")
            }

            val resp = try {
                httpClient.call(ctx.builder).receive<HttpResponse>()
            } catch (ex: ConnectException) {
                log.debug("[$callId] ConnectException: ${ex.message}")
                continue
            }

            if (resp.status.value in 500..599) {
                log.info("[$callId] Failed with status ${resp.status}. Retrying...")
                continue
            }

            val result = parseResponse(resp, call, callId)
            val end = System.currentTimeMillis()
            log.debug("[$callId] (Time: ${end - start}ms. Attempts: $attempts) <- ${result.toString().take(100)}")
            return result
        }
    }

    private suspend fun <S : Any> parseResponseToType(
        resp: HttpResponse,
        call: CallDescription<*, *, *>,
        type: TypeReference<S>
    ): S {
        val kClass = type.kClass
        val companionInstance = kClass.companionObjectInstance

        @Suppress("UNCHECKED_CAST")
        return when {
            kClass == Unit::class -> Unit as S

            companionInstance is HttpClientConverter.IngoingBody<*> -> {
                companionInstance as HttpClientConverter.IngoingBody<S>

                companionInstance.clientIngoingBody(call, resp, type)
            }

            else -> {
                // TODO Blocking
                defaultMapper.readValue<S>(resp.content.toInputStream(), type)
            }
        }
    }

    private suspend fun <E : Any, R : Any, S : Any> parseResponse(
        resp: HttpResponse,
        call: CallDescription<R, S, E>,
        callId: Int
    ): IngoingCallResponse<S, E> {
        return if (resp.status.isSuccess()) {
            IngoingCallResponse.Ok(parseResponseToType(resp, call, call.successType), resp.status)
        } else {
            IngoingCallResponse.Error(
                runCatching {
                    parseResponseToType(resp, call, call.errorType)
                }.getOrElse {
                    log.trace("[$callId] Exception while de-serializing unsuccessful message")
                    log.trace(it.stackTraceToString())
                    null
                },
                resp.status
            )
        }
    }

    private fun defaultBodySerialization(
        payload: Any,
        call: CallDescription<*, *, *>
    ): OutgoingContent {
        return if (payload is HttpClientConverter.OutgoingBody) {
            payload.clientOutgoingBody(call)
        } else {
            TextContent(
                defaultMapper.writeValueAsString(payload),
                ContentType.Application.Json.withCharset(Charsets.UTF_8)
            )
        }
    }

    private fun <R : Any> HttpRequest<R, *, *>.serializeBody(
        request: R,
        call: CallDescription<*, *, *>
    ): OutgoingContent {
        return when (val body = body) {
            is HttpBody.BoundToEntireRequest<*> -> {
                @Suppress("UNCHECKED_CAST")
                body as HttpBody.BoundToEntireRequest<R>

                body.outgoingConverter?.invoke(request) ?: defaultBodySerialization(request, call)
            }

            is HttpBody.BoundToSubProperty<R, *> -> {
                @Suppress("UNCHECKED_CAST")
                body as HttpBody.BoundToSubProperty<R, Any>

                val value = body.property.get(request) ?: return EmptyContent
                body.outgoingConverter?.invoke(value) ?: defaultBodySerialization(value, call)
            }

            null -> EmptyContent
        }
    }

    private fun <R : Any> HttpRequest<R, *, *>.serializeHeaders(
        request: R,
        call: CallDescription<R, *, *>
    ): List<Pair<String, String>> {
        if (headers == null) return emptyList()

        return headers.parameters.mapNotNull {
            when (it) {
                is HttpHeaderParameter.Simple -> it.header to it.value
                is HttpHeaderParameter.Present -> it.header to "true"

                is HttpHeaderParameter.Property<R, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    it as HttpHeaderParameter.Property<R, Any?>

                    val value = it.property.get(request) ?: return@mapNotNull null

                    val headerValue = when {
                        it.outgoingConverter != null -> it.outgoingConverter.invoke(value)
                        value is HttpClientConverter.OutgoingHeader -> value.clientOutgoingHeader(call, it.header)
                        else -> value.toString()
                    }

                    it.header to headerValue
                }
            }
        }
    }

    private fun <R : Any> HttpRequest<R, *, *>.resolveEndpoint(
        request: R,
        call: CallDescription<*, *, *>
    ): String {
        val primaryPath = serializePathSegments(request, call)
        val queryPathMap = serializeQueryParameters(request, call) ?: emptyMap()
        val queryPath = encodeQueryParamsToString(queryPathMap)

        return path.basePath.removeSuffix("/") + "/" + primaryPath + queryPath
    }

    private fun <R : Any> HttpRequest<R, *, *>.serializePathSegments(
        request: R,
        call: CallDescription<*, *, *>
    ): String {
        return path.segments.asSequence().mapNotNull {
            when (it) {
                is HttpPathSegment.Simple -> it.text
                is HttpPathSegment.Remaining -> ""
                is HttpPathSegment.Property<R, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    it as HttpPathSegment.Property<R, Any?>

                    val value = it.property.get(request)

                    when {
                        value == null -> null
                        it.outgoingConverter != null -> it.outgoingConverter.invoke(value)
                        value is HttpClientConverter.OutgoingPath -> value.clientOutgoingPath(call)
                        else -> value.toString()
                    }
                }
            }
        }.joinToString("/")
    }

    private fun <R : Any> HttpRequest<R, *, *>.serializeQueryParameters(
        request: R,
        call: CallDescription<*, *, *>
    ): Map<String, String>? {
        return params?.parameters?.mapNotNull {
            when (it) {
                is HttpQueryParameter.Property<R, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    it as HttpQueryParameter.Property<R, Any?>

                    val value = it.property.get(request) ?: return@mapNotNull null

                    val serialized = when {
                        it.outgoingConverter != null -> it.outgoingConverter.invoke(value)
                        value is HttpClientConverter.OutgoingQuery -> value.clientOutgoingQuery(call)
                        else -> value.toString()
                    }

                    Pair(it.property.name, serialized)
                }
            }
        }?.toMap()
    }

    private fun String.urlEncode() = URLEncoder.encode(this, "UTF-8")
    private fun encodeQueryParamsToString(queryPathMap: Map<String, String>): String {
        return queryPathMap
            .map { it.key.urlEncode() + "=" + it.value.urlEncode() }
            .joinToString("&")
            .takeIf { it.isNotEmpty() }
            ?.let { "?$it" } ?: ""
    }

    companion object : Loggable {
        override val log = logger()
    }
}

object HttpClientConverter {
    interface OutgoingPath {
        fun clientOutgoingPath(call: CallDescription<*, *, *>): String
    }

    interface OutgoingQuery {
        fun clientOutgoingQuery(call: CallDescription<*, *, *>): String
    }

    interface OutgoingBody {
        fun clientOutgoingBody(call: CallDescription<*, *, *>): OutgoingContent
    }

    interface OutgoingHeader {
        fun clientOutgoingHeader(call: CallDescription<*, *, *>, headerName: String): String
    }

    interface OutgoingCustomHeaders {
        fun clientAddCustomHeaders(call: CallDescription<*, *, *>): List<Pair<String, String>>
    }

    interface IngoingBody<T : Any> {
        suspend fun clientIngoingBody(
            description: CallDescription<*, *, *>,
            call: HttpResponse,
            typeReference: TypeReference<T>
        ): T
    }
}
