package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.api.QueryInternalWebParametersResponse
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.utils.EmptyContent
import io.ktor.features.origin
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.request.header
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.host
import io.ktor.routing.route
import io.ktor.util.date.GMTDate
import io.ktor.util.pipeline.PipelineContext
import io.ktor.util.toMap
import io.ktor.websocket.webSocket
import kotlinx.coroutines.io.ByteReadChannel
import kotlin.collections.set

private const val SDU_CLOUD_REFRESH_TOKEN = "refreshToken"
private const val APP_REFRESH_TOKEN = "appRefreshToken"

class WebService(
    private val authenticationService: AuthenticationService,
    private val tunnelManager: TunnelManager,
    /**
     * For some dev environments it might not be possible to set a cookie on the app domain. We allow configuration
     * to skip the authentication.
     */
    private val performAuthentication: Boolean,
    private val prefix: String = "app-",
    private val domain: String = "cloud.sdu.dk"
) {
    private val client = HttpClient(OkHttp)

    private val jobIdToJob = HashMap<String, VerifiedJob>()

    // A bit primitive, should work for most cases.
    private fun String.escapeToRegex(): String = replace(".", "\\.")

    fun install(routing: Route): Unit = with(routing) {
        get("${AppKubernetesDescriptions.baseContext}/authorize-app/{id}") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            if (performAuthentication) {
                val job = jobIdToJob[id] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }

                val ingoingToken = call.request.cookies[SDU_CLOUD_REFRESH_TOKEN] ?: run {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                val validated = authenticationService.validate(ingoingToken)
                if (validated == null || job.owner != validated.principal.username) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                call.response.cookies.append(
                    name = APP_REFRESH_TOKEN,
                    value = ingoingToken,
                    secure = call.request.origin.scheme == "https",
                    httpOnly = true,
                    expires = GMTDate(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 30)),
                    path = "/",
                    domain = domain
                )
            }

            call.respondRedirect("http://$prefix$id.$domain/")
        }

        route("{path...}") {
            host(Regex("${prefix.escapeToRegex()}.*\\.${domain.escapeToRegex()}")) {
                webSocket {
                    val path = call.request.path()
                    val host = call.request.header(HttpHeaders.Host) ?: ""
                    val id = host.substringAfter(prefix).substringBefore(".")
                    if (!host.startsWith(prefix)) {
                        call.respondText(status = HttpStatusCode.NotFound) { "Not found" }
                        return@webSocket
                    }

                    log.info("Accepting websocket for job $id at ${call.request.path()}")
                    if (!authorizeUser(call, id)) {
                        return@webSocket
                    }

                    val tunnel = createTunnel(id)
                    runWSProxy(tunnel, path = path)
                }

                handle {
                    val host = call.request.header(HttpHeaders.Host) ?: ""
                    val id = host.substringAfter(prefix).substringBefore(".")
                    if (!host.startsWith(prefix)) {
                        call.respondText(status = HttpStatusCode.NotFound) { "Not found" }
                        return@handle
                    }

                    val path = call.request.path()
                    log.info("Accepting call for job $id at $path")
                    if (!authorizeUser(call, id)) {
                        return@handle
                    }
                    val tunnel = createTunnel(id)
                    proxyResponseToClient(proxyToServer(tunnel))
                }
            }
        }

        return@with
    }

    private suspend fun authorizeUser(call: ApplicationCall, jobId: String): Boolean {
        if (!performAuthentication) return true
        val job = jobIdToJob[jobId] ?: run {
            call.respondText(status = HttpStatusCode.BadRequest) { "Bad request (Invalid job)." }
            return false
        }

        val token = call.request.cookies[APP_REFRESH_TOKEN] ?: run {
            call.respondText(status = HttpStatusCode.Unauthorized) { "Unauthorized." }
            return false
        }

        val principal = authenticationService.validate(token) ?: run {
            call.respondText(status = HttpStatusCode.Unauthorized) { "Unauthorized." }
            return false
        }

        if (job.owner != principal.principal.username) {
            call.respondText(status = HttpStatusCode.Unauthorized) { "Unauthorized." }
            return false
        }

        return true
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.proxyToServer(tunnel: Tunnel): HttpClientCall {
        val requestPath = call.parameters.getAll("path")?.joinToString("/") ?: "/"
        val requestQueryParameters = call.request.queryParameters
        val method = call.request.httpMethod
        val requestCookies = HashMap(call.request.cookies.rawCookies).apply {
            // Remove authentication tokens
            remove(APP_REFRESH_TOKEN)
            remove(SDU_CLOUD_REFRESH_TOKEN)
        }

        val requestHeaders = call.request.headers.toMap().mapKeys { it.key.toLowerCase() }.toMutableMap().apply {
            remove(HttpHeaders.Referrer.toLowerCase())
            remove(HttpHeaders.ContentLength.toLowerCase())
            remove(HttpHeaders.ContentType.toLowerCase())
            remove(HttpHeaders.TransferEncoding.toLowerCase())
            remove(HttpHeaders.Cookie.toLowerCase())
            remove(HttpHeaders.Upgrade.toLowerCase())
            remove(HttpHeaders.Host.toLowerCase())
            remove(HttpHeaders.Origin.toLowerCase())

            put(HttpHeaders.Host, listOf("127.0.0.1:${tunnel.localPort}"))
            put(HttpHeaders.Referrer, listOf("http://127.0.0.1:${tunnel.localPort}/"))
            put(HttpHeaders.Origin, listOf("http://127.0.0.1:${tunnel.localPort}"))
        }

        val requestContentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        val requestContentType = call.request.header(HttpHeaders.ContentType)?.let {
            runCatching {
                ContentType.parse(it)
            }.getOrNull()
        } ?: ContentType.Application.OctetStream

        val hasRequestBody = requestContentLength != null ||
                call.request.header(HttpHeaders.TransferEncoding) != null

        val requestBody: OutgoingContent = if (!hasRequestBody) {
            EmptyContent
        } else {
            object : OutgoingContent.ReadChannelContent() {
                override val contentLength: Long? = requestContentLength
                override val contentType: ContentType = requestContentType
                override fun readFrom(): ByteReadChannel = call.request.receiveChannel()
            }
        }

        val request = HttpRequestBuilder().apply {
            this.method = method
            this.url {
                protocol = URLProtocol.HTTP
                host = "127.0.0.1"
                port = tunnel.localPort
                encodedPath = requestPath
                parameters.appendAll(requestQueryParameters)
            }

            this.body = requestBody
            this.headers {
                requestHeaders.forEach { (header, values) ->
                    appendAll(header, values)
                }

                append(
                    HttpHeaders.Cookie,
                    requestCookies.entries.joinToString(";") { "${it.key}=${it.value}" }
                )
            }
        }

        return client.execute(request)
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.proxyResponseToClient(clientCall: HttpClientCall) =
        with(clientCall) {
            val statusCode = response.status
            val responseHeaders = response.headers.toMap().mapKeys { it.key.toLowerCase() }.toMutableMap().apply {
                remove(HttpHeaders.Server.toLowerCase())
                remove(HttpHeaders.ContentLength.toLowerCase())
                remove(HttpHeaders.ContentType.toLowerCase())
                remove(HttpHeaders.TransferEncoding.toLowerCase())
                remove(HttpHeaders.Upgrade.toLowerCase())
            }

            val responseContentLength = response.contentLength()
            val responseContentType = response.contentType()

            val hasResponseBody =
                responseContentLength != null || response.headers[HttpHeaders.TransferEncoding] != null

            val responseBody: OutgoingContent = if (!hasResponseBody) {
                EmptyContent
            } else {
                object : OutgoingContent.ReadChannelContent() {
                    override val contentLength: Long? = responseContentLength
                    override val contentType: ContentType? = responseContentType
                    override fun readFrom(): ByteReadChannel = response.content
                }
            }

            responseHeaders.forEach { (header, values) ->
                values.forEach { value ->
                    call.response.headers.append(header, value)
                }
            }

            call.respond(statusCode, responseBody)
        }

    fun queryParameters(job: VerifiedJob): QueryInternalWebParametersResponse {
        jobIdToJob[job.id] = job
        return QueryInternalWebParametersResponse(
            "${AppKubernetesDescriptions.baseContext}/authorize-app/${job.id}"
        )
    }

    private suspend fun createTunnel(incomingId: String): Tunnel {
        val job = jobIdToJob[incomingId] ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val remotePort = job.application.invocation.web?.port ?: 80
        return tunnelManager.createTunnel(incomingId, remotePort)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
