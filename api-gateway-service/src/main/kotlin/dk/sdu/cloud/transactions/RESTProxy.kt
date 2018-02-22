package dk.sdu.cloud.transactions

import com.github.zafarkhaja.semver.Version
import dk.sdu.cloud.service.*
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.content.OutgoingContent
import io.ktor.features.origin
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.request.*
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.method
import io.ktor.routing.route
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.zookeeper.ZooKeeper
import org.asynchttpclient.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.URL
import java.util.*
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

sealed class RESTProxyException(message: String, val code: HttpStatusCode) : Exception(message)
class RESTNoServiceAvailable : RESTProxyException("Gateway timeout", HttpStatusCode.GatewayTimeout)

private val httpClient = DefaultAsyncHttpClient()

class RESTProxy(
    private val targets: List<ServiceDefinition>,
    private val zk: ZooKeeper,
    private val kafkaProducer: KafkaProducer<String, String>
) {
    private val random = Random()

    private val requestHeaderBlacklist = setOf(
        "Job-Id",
        "Host",
        HttpHeaders.XForwardedFor
    ).map { it.normalizeHeader() }.toSet()

    private val responseHeaderBlacklist = setOf(
        HttpHeaders.Connection,
        HttpHeaders.Server,
        HttpHeaders.Date,
        HttpHeaders.ContentLength,
        HttpHeaders.ContentType
    ).map { it.normalizeHeader() }.toSet()

    private fun String.normalizeHeader() = toUpperCase()

    companion object {
        private val log = LoggerFactory.getLogger(RESTProxy::class.java)
    }

    fun configure(route: Route): Unit = with(route) {
        val eventProducer = kafkaProducer.forStream(KafkaHttpLogger.httpLogsStream)
        targets.forEach { service ->
            service.restDescriptions.flatMap { it.descriptions }.filter { it.shouldProxyFromGateway }.forEach {

                route(it.template) {
                    method(HttpMethod.parse(it.method.name())) {
                        handle {
                            try {
                                val jobId = UUID.randomUUID().toString()
                                val (host, instance) = findService(service)

                                val userAgent = call.request.userAgent()
                                val origin = call.request.origin.remoteHost

                                async {
                                    eventProducer.emit(
                                        jobId, HttpProxyCallLogEntry(
                                            jobId, instance,
                                            userAgent, origin
                                        )
                                    )
                                }

                                call.proxyJobTo(jobId, host)
                            } catch (ex: RESTProxyException) {
                                when (ex) {
                                    is RESTNoServiceAvailable -> {
                                        log.warn(
                                            "Unable to proxy request to target service. Unable to find " +
                                                    "any running service!"
                                        )
                                        log.warn("Service is: ${service.manifest}")
                                    }
                                }
                                call.respond(ex.code)
                            } catch (ex: Exception) {
                                log.warn("Caught unexpected exception while proxying")
                                log.warn(ex.stackTraceToString())
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun findService(service: ServiceDefinition): Pair<URL, ServiceInstance> {
        val parsedVersion = Version.valueOf(service.manifest.version)
        val onlyIntegerVersion = with(parsedVersion) { "$majorVersion.$minorVersion.$patchVersion" }

        val services = with(service.manifest) {
            zk.listServicesWithStatus(name, onlyIntegerVersion).values.firstOrNull()
        }?.takeIf { it.isNotEmpty() } ?: throw RESTNoServiceAvailable()

        // TODO FIXME proxying using https
        val resolvedService = services[random.nextInt(services.size)]
        return Pair(
            URL("http://${resolvedService.instance.hostname}:${resolvedService.instance.port}"),
            resolvedService.instance
        )
    }

    private suspend fun ApplicationCall.proxyJobTo(
        jobId: String,
        host: URL,
        endpoint: String = request.path(),
        includeQueryString: Boolean = true,
        proxyMethod: HttpMethod = request.httpMethod
    ) {
        val queryString = if (includeQueryString) '?' + request.queryString() else ""
        val endpointUrl = URL(host, endpoint + queryString)

        // The presence of a message-body in a request is signaled by the inclusion of a Content-Length or
        // Transfer-Encoding header field in the request's message-headers (section 4.3, RFC 2616)
        //
        // NOTE(Dan): receiveOrNull<InputStream>() will return a non-null result even if the request carries no body.
        // This can cause some servers to (correctly) return 501 NOT IMPLEMENTED on certain HTTP methods. For example,
        // if the server has not implemented chunked transfer encoding for GET messages.
        val stream = if (
            request.header(HttpHeaders.ContentLength) != null ||
            request.header(HttpHeaders.TransferEncoding) != null
        ) {
            receiveOrNull<InputStream>()
        } else {
            null
        }

        val streamingHttp = StreamingHttpRequest(endpointUrl.toString()) {
            request.headers.forEach { header, values ->
                if (header.normalizeHeader() !in requestHeaderBlacklist) {
                    values.forEach { addHeader(header, it) }
                }
            }

            setHeader(HttpHeaders.XForwardedFor, request.origin.remoteHost)
            setHeader("Job-Id", jobId)
            setMethod(proxyMethod.value)

            if (stream != null) {
                setBody(stream)
            }
        }

        var contentLength: Long? = null
        var contentType: ContentType? = null
        try {
            streamingHttp.retrieveStatusAndHeaders(
                statusHandler = {
                    response.status(HttpStatusCode.fromValue(it))
                },

                headerHandler = { headers ->
                    headers.forEach {
                        if (it.key.normalizeHeader() !in responseHeaderBlacklist) {
                            response.header(it.key, it.value)
                        }

                        if (it.key.normalizeHeader() == HttpHeaders.ContentLength.normalizeHeader()) {
                            contentLength = it.value.toLongOrNull()
                        }

                        if (it.key.normalizeHeader() == HttpHeaders.ContentType.normalizeHeader()) {
                            contentType = ContentType.parse(it.value)
                        }
                    }
                }
            )

            respondDirectWrite(contentLength, contentType) {
                streamingHttp.retrieveBody {
                    writeFully(it.bodyByteBuffer)
                }
            }
        } catch (ex: IOException) {
            log.warn("Service is unavailable: $endpointUrl")
            log.warn(ex.stackTraceToString())
            respond(HttpStatusCode.ServiceUnavailable)
        }
    }
}

internal typealias NettyHttpHeaders = io.netty.handler.codec.http.HttpHeaders

class StreamingHttpRequest(
    private val endpoint: String,
    private val config: BoundRequestBuilder.() -> Unit
) {
    private var continuation: Continuation<Unit>? = null
    private var cachedThrowable: Throwable? = null
    private var isDone = false
    private var bodyPartHandler: ((HttpResponseBodyPart) -> Unit)? = null
    private var statusHandler: ((Int) -> Unit)? = null
    private var headerHandler: ((NettyHttpHeaders) -> Unit)? = null
    private val cachedBodyParts = ArrayList<HttpResponseBodyPart>()

    private val lock = Any()

    private val asyncHandler = object : AsyncHandler<Response?> {
        override fun onStatusReceived(responseStatus: HttpResponseStatus): AsyncHandler.State {
            statusHandler!!(responseStatus.statusCode)
            return AsyncHandler.State.CONTINUE
        }

        override fun onHeadersReceived(headers: NettyHttpHeaders): AsyncHandler.State {
            headerHandler!!(headers)
            continuation!!.resume(Unit)
            continuation = null
            return AsyncHandler.State.CONTINUE
        }

        override fun onBodyPartReceived(bodyPart: HttpResponseBodyPart): AsyncHandler.State {
            synchronized(lock) {
                val capturedHandler = bodyPartHandler
                if (capturedHandler == null) {
                    cachedBodyParts.add(bodyPart)
                } else {
                    cachedBodyParts.forEach { capturedHandler(it) }
                    cachedBodyParts.clear()

                    capturedHandler(bodyPart)
                }
            }
            return AsyncHandler.State.CONTINUE
        }

        override fun onCompleted(): Response? {
            synchronized(lock) {
                val capturedContinuation = continuation
                if (capturedContinuation == null) {
                    isDone = true
                } else {
                    val bodyPartHandler = bodyPartHandler
                    if (bodyPartHandler != null) {
                        cachedBodyParts.forEach(bodyPartHandler)
                        cachedBodyParts.clear()
                    }

                    capturedContinuation.resume(Unit)
                }
            }
            return null
        }

        override fun onThrowable(t: Throwable) {
            synchronized(lock) {
                val capturedContinuation = continuation
                if (capturedContinuation == null) {
                    cachedThrowable = t
                } else {
                    capturedContinuation.resumeWithException(t)
                }
            }
        }
    }

    suspend fun retrieveStatusAndHeaders(
        statusHandler: (Int) -> Unit,
        headerHandler: (NettyHttpHeaders) -> Unit
    ): Unit = suspendCoroutine { continuation ->
        this.continuation = continuation
        this.statusHandler = statusHandler
        this.headerHandler = headerHandler

        val preparedRequest = httpClient.prepareGet(endpoint).also(config)
        preparedRequest.execute(asyncHandler)
    }

    suspend fun retrieveBody(
        bodyPartHandler: suspend (HttpResponseBodyPart) -> Unit
    ): Unit = suspendCoroutine { continuation ->
        this.continuation = continuation
        this.bodyPartHandler = { runBlocking { bodyPartHandler(it) } }

        val throwable = cachedThrowable
        if (throwable != null) {
            continuation.resumeWithException(throwable)
        } else if (isDone) {
            cachedBodyParts.forEach {
                runBlocking { bodyPartHandler(it) }
            }
            cachedBodyParts.clear()
            continuation.resume(Unit)
        }
    }
}

suspend fun ApplicationCall.validateRequestAndPrepareJobHeader(respond: Boolean = true): RequestHeader? {
    // TODO This probably shouldn't do a response for us
    val jobId = UUID.randomUUID().toString()
    val token = request.bearer() ?: return run {
        if (respond) respond(HttpStatusCode.Unauthorized)
        else response.status(HttpStatusCode.Unauthorized)

        null
    }
    return RequestHeader(jobId, token)
}

fun ApplicationRequest.bearer(): String? {
    val auth = authorization() ?: return null
    if (!auth.startsWith("Bearer ")) return null

    return auth.substringAfter("Bearer ")
}


suspend fun ApplicationCall.respondDirectWrite(
    size: Long? = null,
    contentType: ContentType? = null,
    writer: suspend ByteWriteChannel.() -> Unit
) {
    val message = DirectWriteContent(writer, size, contentType)
    return respond(message)
}

class DirectWriteContent(
    private val writer: suspend ByteWriteChannel.() -> Unit,
    override val contentLength: Long? = null,
    override val contentType: ContentType? = null
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        writer(channel)
    }
}
