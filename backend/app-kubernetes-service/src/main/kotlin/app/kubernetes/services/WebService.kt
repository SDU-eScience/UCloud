package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.app.orchestrator.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.orchestrator.api.QueryInternalWebParametersResponse
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.service.BroadcastingStream
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.features.origin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.request.host
import io.ktor.response.header
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.util.date.GMTDate

private const val SDU_CLOUD_REFRESH_TOKEN = "refreshToken"

class WebService(
    private val authenticationService: AuthenticationService,

    /**
     * For some dev environments it might not be possible to set a cookie on the app domain. We allow configuration
     * to skip the authentication.
     */
    private val performAuthentication: Boolean,
    private val jobCache: VerifiedJobCache,
    private val broadcastingStream: BroadcastingStream,
    private val cookieName: String = "appRefreshToken",
    private val prefix: String,
    private val domain: String,
    private val k8: K8Dependencies,
    private val devMode: Boolean = false
) {
    fun install(routing: Route): Unit = with(routing) {
        // Called when entering the application. This sets the cookie containing the refresh token.
        get("${AppKubernetesDescriptions.baseContext}/authorize-app/{id}") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val job = jobCache.findJob(id) ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            if (!devMode) {
                if (performAuthentication) {
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
                        name = cookieName,
                        value = ingoingToken,
                        secure = call.request.origin.scheme == "https",
                        httpOnly = true,
                        expires = GMTDate(Time.now() + (1000L * 60 * 60 * 24 * 30)),
                        path = "/",
                        domain = domain
                    )
                }

                val urlId = if (job.url == null) {
                    id
                } else {
                    job.url
                }

                call.respondRedirect("http://$prefix$urlId.$domain/")
            } else {
                // Development mode will attempt to use minikube and use node-port
                @Suppress("BlockingMethodInNonBlockingContext")
                run {
                    log.info("Attempting to use minikube")
                    val start = ProcessBuilder("minikube", "ip").start()
                    val minikubeIpAddress = start.inputStream.bufferedReader().readText().lines().first()
                    start.waitFor()

                    val nodeport = k8.nameAllocator
                        .findServices(job.id)
                        .list()
                        .items
                        .first()
                        .spec
                        .ports
                        .first()
                        .nodePort

                    call.respondRedirect("http://${minikubeIpAddress}:$nodeport")
                }
            }
        }

        // Called by envoy before every single request. We are allowed to control the "Cookie" header.
        route("${AppKubernetesDescriptions.baseContext}/app-authorization/{...}") {
            handle {
                val host = call.request.host()
                if (!host.startsWith(prefix) || !host.endsWith(domain)) {
                    call.respondText("Forbidden", status = HttpStatusCode.Forbidden)
                    return@handle
                }

                val jobId = host.removePrefix(prefix).removeSuffix(".$domain")
                if (authorizeUser(call, jobId)) {
                    val requestCookies = HashMap(call.request.cookies.rawCookies).apply {
                        // Remove authentication tokens
                        remove(cookieName)
                        remove(SDU_CLOUD_REFRESH_TOKEN)
                    }
                    call.response.header(
                        HttpHeaders.Cookie,
                        requestCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                    )

                    call.respondText("", status = HttpStatusCode.OK)
                }
            }
        }
    }

    private suspend fun authorizeUser(call: ApplicationCall, jobId: String, sendResponse: Boolean = true): Boolean {
        if (!performAuthentication) return true
        val job = jobCache.findJob(jobId) ?: run {
            if (sendResponse) {
                call.respondText(status = HttpStatusCode.BadRequest) { "Bad request (Invalid job)." }
            }
            return false
        }

        if (job.url != null) {
            return true
        }

        val token = call.request.cookies[cookieName] ?: run {
            if (sendResponse) {
                call.respondText(status = HttpStatusCode.Forbidden) { "Unauthorized." }
            }
            return false
        }

        val principal = authenticationService.validate(token) ?: run {
            if (sendResponse) {
                call.respondText(status = HttpStatusCode.Forbidden) { "Unauthorized." }
            }
            return false
        }

        if (job.owner != principal.principal.username) {
            if (sendResponse) {
                call.respondText(status = HttpStatusCode.Forbidden) { "Unauthorized." }
            }
            return false
        }

        return true
    }

    suspend fun queryParameters(job: VerifiedJob): QueryInternalWebParametersResponse {
        jobCache.cacheJob(job)
        broadcastingStream.broadcast(ProxyEvent(job.id, true), ProxyEvents.events)
        return QueryInternalWebParametersResponse("${AppKubernetesDescriptions.baseContext}/authorize-app/${job.id}")
    }

    companion object : Loggable {
        override val log = logger()
    }
}
