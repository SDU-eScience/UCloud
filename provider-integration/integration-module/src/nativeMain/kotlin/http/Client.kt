package dk.sdu.cloud.http

import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.tcp.*
import dk.sdu.cloud.utils.ObjectPool
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.random.Random

@Serializable
data class RawOutgoingHttpPayload(
    val contentType: String,
    val payload: ByteArray
)

class OutgoingHttpCall(
    private val debugCall: CallDescription<*, *, *>
) : OutgoingCall {
    var attempts: Int = 0
    override val attributes: AttributeContainer = AttributeContainer()

    override fun toString(): String = "OutgoingHttpCall($debugCall)"

    companion object : OutgoingCallCompanion<OutgoingHttpCall> {
        override val klass = OutgoingHttpCall::class
        override val attributes = AttributeContainer()
    }
}

class OutgoingHttpRequestInterceptor : OutgoingRequestInterceptor<OutgoingHttpCall, OutgoingHttpCall.Companion> {
    override val companion = OutgoingHttpCall.Companion

    private data class HttpConnection(
        val read: ByteBuffer,
        val write: ByteBuffer,
        val tcp: TcpConnection,
    )

    private class HttpConnectionPool(private val target: HostInfo) : ObjectPool<HttpConnection>(10) {
        override fun produceItem(): HttpConnection {
            try {
                return HttpConnection(
                    allocateDirect(1024 * 32),
                    allocateDirect(1024 * 32),
                    tcpConnect(
                        if (target.scheme == "https" || target.port == 443) TransportSecurity.TLS
                        else TransportSecurity.PLAIN,
                        target.host,
                        target.port ?: when (target.scheme) {
                            "https" -> 443
                            else -> 80
                        }
                    )
                )
            } catch (ex: TcpException) {
                throw RPCException.fromStatusCode(HttpStatusCode.BadGateway)
            }
        }

        override fun isValid(item: HttpConnection): Boolean {
            return item.tcp.isOpen()
        }

        override fun reset(item: HttpConnection) {
            // Do nothing
        }

        override fun onDelete(item: HttpConnection) {
            item.tcp.close()
        }
    }

    private val connectionPoolMutex = Mutex()
    private val connectionPool = HashMap<HostInfo, HttpConnectionPool>()

    private suspend inline fun <R> useConnection(
        target: HostInfo,
        block: (HttpConnection) -> R
    ): R {
        val pool = connectionPoolMutex.withLock {
            val cachedPool = connectionPool[target]
            if (cachedPool == null) {
                val newPool = HttpConnectionPool(target)
                connectionPool[target] = newPool
                newPool
            } else {
                cachedPool
            }
        }

        val (ticket, conn) = pool.borrow()
        try {
            return block(conn)
        } finally {
            pool.recycleInstance(ticket)
        }
    }

    fun install(
        client: RpcClient,
        targetHostResolver: OutgoingHostResolver
    ) {
        with(client) {
            attachFilter(OutgoingHostResolverInterceptor(targetHostResolver))
            attachRequestInterceptor(this@OutgoingHttpRequestInterceptor)
        }
    }

    override suspend fun <R : Any, S : Any, E : Any> finalizeCall(
        call: CallDescription<R, S, E>,
        request: R,
        ctx: OutgoingHttpCall
    ): IngoingCallResponse<S, E> {
        val callId = Random.nextInt(10000) // A non-unique call ID for logging purposes only
        val start = Time.now()
        val shortRequestMessage = request.toString().take(100)

        val http = call.http
        val endpoint = http.resolveEndpoint(request, call).removeSuffix("/")
        try {
            useConnection(ctx.attributes.outgoingTargetHost) { connection ->
                log.debug("[$callId] -> ${call.fullName}: $shortRequestMessage")

                val contentType: String? = when {
                    request is RawOutgoingHttpPayload -> request.contentType
                    http.body is HttpBody.BoundToEntireRequest<*> -> "application/json"
                    else -> null
                }

                val output: ByteArray? = when {
                    request is RawOutgoingHttpPayload -> request.payload
                    http.body is HttpBody.BoundToEntireRequest<*> -> {
                        defaultMapper.encodeToString(call.requestType, request).encodeToByteArray()
                    }

                    else -> null
                }

                connection.write.clear()

                run {
                    // Write request line
                    connection.write.putAscii(http.method.value)
                    connection.write.putAscii(" ")
                    connection.write.put(endpoint.encodeToByteArray())
                    connection.write.putAscii(" HTTP/1.1\r\n")
                }

                run {
                    // Write headers to buffer
                    run {
                        // Request headers
                        val headers = http.serializeHeaders(request)
                        for (header in headers) {
                            connection.write.putAscii(header.header)
                            connection.write.putAscii(": ")
                            connection.write.putAscii(header.value)
                            connection.write.putAscii("\r\n")
                        }
                    }

                    run {
                        // Hard-coded headers
                        connection.write.putAscii("Connection: keep-alive\r\n")
                        connection.write.putAscii("User-Agent: UCloud-Integration-Module\r\n")
                        connection.write.putAscii("Host: ")
                        connection.write.putAscii(ctx.attributes.outgoingTargetHost.host)
                        connection.write.putAscii("\r\n")
                        connection.write.putAscii("Accept: */*\r\n")
                    }

                    run {
                        // Payload headers
                        if (contentType != null) {
                            connection.write.putAscii("Content-Type: ")
                            connection.write.putAscii(contentType)
                            connection.write.putAscii("\r\n")
                        }

                        if (output != null) {
                            connection.write.putAscii("Content-Length: ")
                            connection.write.putAscii(output.size.toString())
                            connection.write.putAscii("\r\n")
                        }
                    }

                    run {
                        // Authorization headers
                        val authToken = ctx.attributes.outgoingAuthToken
                        if (authToken != null) {
                            connection.write.putAscii("Authorization: Bearer ")
                            connection.write.putAscii(authToken)
                            connection.write.putAscii("\r\n")
                        }
                    }

                    connection.write.putAscii("\r\n")
                }

                /*
                println(connection.write.rawMemory.decodeToString(connection.write.readerIndex, connection.write.writerIndex))
                 */

                run {
                    // Write payload
                    if (output != null && output.size > connection.write.writerSpaceRemaining()) {
                        if (output.size > connection.write.capacity()) {
                            throw IllegalStateException("Output payload is too big and this isn't handled yet! ${output.size}")
                        }

                        connection.tcp.write(connection.write)
                        connection.write.clear()
                    }

                    if (output != null) {
                        connection.write.put(output)
                    }
                }

                // Flush request
                connection.tcp.write(connection.write)

                // Await response line
                val responseLine = connection.tcp.readUntilDelimiter('\n'.code.toByte(), connection.read)
                val responseCode = responseLine.removePrefix("HTTP/1.1 ").substringBefore(' ').toIntOrNull()
                val responseStatus = responseLine.removePrefix("HTTP/1.1 ").substringAfter(' ')

                if (responseCode == null || !responseLine.startsWith("HTTP/1.1")) {
                    connection.tcp.close()
                    throw RPCException("Bad request received from server: $responseLine", HttpStatusCode.BadGateway)
                }

                // Await headers
                val responseHeaders = ArrayList<Header>()
                while (true) {
                    if (responseHeaders.size > 1000) {
                        connection.tcp.close()
                        throw RPCException(
                            "Received too many headers from server (is state corrupt?)",
                            HttpStatusCode.BadGateway
                        )
                    }

                    val headerLine = connection.tcp.readUntilDelimiter('\n'.code.toByte(), connection.read)
                    if (headerLine == "\r") break

                    if (!headerLine.contains(":")) {
                        connection.tcp.close()
                        throw RPCException("Bad header received from server: '$headerLine'", HttpStatusCode.BadGateway)
                    }

                    val headerName = headerLine.substringBefore(':')
                    val headerValue = headerLine.substringAfter(':').trim()
                    responseHeaders.add(Header(headerName, headerValue))
                }

                // Figure out if we should expect a payload
                val contentLength = responseHeaders.find { it.header.equals("Content-Length", ignoreCase = true) }
                val transferEncoding = responseHeaders.find { it.header.equals("Transfer-Encoding", ignoreCase = true) }

                if (transferEncoding != null && !transferEncoding.equals("identity")) {
                    connection.tcp.close()
                    throw RPCException("Unable to handle response encoding", HttpStatusCode.BadGateway)
                }

                val parsedContentLength = contentLength?.value?.toLongOrNull()
                if (parsedContentLength == null) {
                    connection.tcp.close()
                    throw RPCException("Unable to handle response length: $contentLength", HttpStatusCode.BadGateway)
                }

                if (parsedContentLength > connection.read.capacity() || parsedContentLength < 0) {
                    throw RPCException(
                        "Payload is too large for the client to handle: $parsedContentLength",
                        HttpStatusCode.BadGateway
                    )
                }

                if (parsedContentLength > 0) {
                    connection.tcp.readAtLeast(parsedContentLength.toInt(), connection.read)
                }
                val payloadAsString = if (parsedContentLength <= 0) {
                    ""
                } else {
                    connection.read.rawMemory.decodeToString(
                        connection.read.readerIndex,
                        connection.read.readerIndex + parsedContentLength.toInt()
                    )
                }
                connection.read.readerIndex += parsedContentLength.toInt()

                val result: IngoingCallResponse<S, E> = if (responseCode in 200..299) {
                    try {
                        IngoingCallResponse.Ok(
                            parseResponseToType(payloadAsString, call.successType),
                            HttpStatusCode(responseCode, responseStatus),
                            ctx,
                        )
                    } catch (ex: SerializationException) {
                        log.debug("Failed to serialize value. Received '$payloadAsString")
                        throw ex
                    }
                } else {
                    IngoingCallResponse.Error(
                        runCatching { parseResponseToType(payloadAsString, call.errorType) }.getOrNull() ?: run {
                            log.debug("Failed to serialize value. Received '$payloadAsString'")
                            null
                        },
                        HttpStatusCode(responseCode, responseStatus),
                        ctx,
                    )
                }

                val end = Time.now()
                log.debug("[$callId] name=${call.fullName} status=${result.statusCode.value} time=${end - start}ms")

                return result
            }
        } catch (ex: MbedTlsException) {
            if (ex.message?.contains("Connection is closed") == true) {
                ctx.attempts++
                if (ctx.attempts < 10) {
                    return finalizeCall(call, request, ctx)
                }
            }
            throw ex
        }
    }

    private fun <S : Any> parseResponseToType(
        resp: String,
        type: KSerializer<S>,
    ): S {
        if (type.descriptor.serialName == "kotlin.Unit") return Unit as S
        return defaultMapper.decodeFromString(type, resp)
    }

    override suspend fun <R : Any, S : Any, E : Any> prepareCall(
        call: CallDescription<R, S, E>,
        request: R
    ): OutgoingHttpCall {
        return OutgoingHttpCall(call)
    }

    private fun <R : Any> HttpRequest<R, *, *>.serializeHeaders(
        request: R,
    ): List<Header> {
        val headers = headers ?: return emptyList()

        return headers.parameters.mapNotNull {
            when (it) {
                is HttpHeaderParameter.Simple -> Header(
                    it.header,
                    base64Encode(it.value.encodeToByteArray()),
                )

                is HttpHeaderParameter.Present -> Header(
                    it.header,
                    base64Encode("true".encodeToByteArray()),
                )

                is HttpHeaderParameter.Property<R, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    it as HttpHeaderParameter.Property<R, Any?>

                    val value = it.property.get(request) ?: return@mapNotNull null

                    Header(
                        it.header,
                        if (it.base64Encoded) base64Encode(value.toString().encodeToByteArray())
                        else value.toString()
                    )
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
        return path.segments.asSequence().map {
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
    }
}
