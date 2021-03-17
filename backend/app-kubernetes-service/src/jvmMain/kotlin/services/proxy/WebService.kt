package dk.sdu.cloud.app.kubernetes.services.proxy

import dk.sdu.cloud.app.kubernetes.api.KubernetesCompute
import dk.sdu.cloud.app.kubernetes.services.*
import dk.sdu.cloud.app.orchestrator.api.InteractiveSessionType
import dk.sdu.cloud.app.orchestrator.api.OpenSession
import dk.sdu.cloud.app.orchestrator.api.ingressPoints
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.k8.KubernetesResources
import dk.sdu.cloud.service.k8.Service
import dk.sdu.cloud.service.k8.getResource
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.date.*

const val cookieName = "ucloud-compute-session"

class WebService(
    private val k8: K8Dependencies,
    private val db: DBContext,
    private val sessions: SessionDao,
    private val prefix: String,
    private val domain: String,
    private val ingressService: IngressService,
    private val devMode: Boolean = false
) {
    // Relatively low maxAge to make sure that we renew the session id regularly
    private val sessionCache = SimpleCache<String, JobIdAndRank>(maxAge = 60_000 * 15L) { sessionId ->
        sessions.findSessionOrNull(db, sessionId, InteractiveSessionType.WEB)
    }

    private val ingressCache = SimpleCache<String, JobIdAndRank>(maxAge = 60_000 * 15L) { domain ->
        ingressService.retrieveJobIdByDomainOrNull(domain)?.let { JobIdAndRank(it, 0) }
    }

    suspend fun createSession(jobAndRank: JobAndRank): OpenSession.Web {
        if (jobAndRank.job.ingressPoints.isNotEmpty()) {
            val domain = ingressService.retrieveDomainsByJobId(jobAndRank.job.id).firstOrNull()
            if (domain != null) {
                return OpenSession.Web(
                    jobAndRank.job.id,
                    jobAndRank.rank,
                    "http://$domain"
                )
            }
        }

        val webSessionId = db.withSession { session ->
            sessions.createSession(session, jobAndRank, InteractiveSessionType.WEB)
        }

        sessionCache.insert(webSessionId, JobIdAndRank(jobAndRank.job.id, jobAndRank.rank))
        return OpenSession.Web(
            jobAndRank.job.id,
            jobAndRank.rank,
            "${KubernetesCompute.baseContext}/authorize-app/$webSessionId"
        )
    }

    fun install(routing: Route): Unit = with(routing) {
        // Called when entering the application. This sets the cookie containing the refresh token.
        get("${KubernetesCompute.baseContext}/authorize-app/{id}") {
            val sessionId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val jobAndRank = sessionCache.get(sessionId) ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            call.response.cookies.append(
                name = cookieName,
                value = sessionId,
                secure = call.request.origin.scheme == "https",
                httpOnly = true,
                expires = GMTDate(Time.now() + (1000L * 60 * 60 * 24 * 30)),
                path = "/",
                domain = domain
            )

            if (!devMode) {
                call.respondRedirect("http://$prefix${jobAndRank.jobId}-${jobAndRank.rank}.$domain/")
            } else {
                // NOTE(Dan): Doesn't currently support multiple ranks
                // Development mode will attempt to use minikube and use node-port
                @Suppress("BlockingMethodInNonBlockingContext")
                run {
                    log.info("Attempting to use minikube")
                    val start = ProcessBuilder("minikube", "ip").start()
                    val minikubeIpAddress = start.inputStream.bufferedReader().readText().lines().first()
                    start.waitFor()

                    val name = k8.nameAllocator.jobIdToJobName(jobAndRank.jobId)
                    val namespace = k8.nameAllocator.jobIdToNamespace(jobAndRank.jobId)

                    val nodePort = k8.client
                        .getResource<Service>(
                            KubernetesResources.services.withNameAndNamespace(
                                name + MinikubePlugin.SERVICE_SUFFIX,
                                namespace
                            )
                        )
                        .spec
                        ?.ports
                        ?.first()
                        ?.nodePort
                        ?: error("No NodePort attached to job: $jobAndRank")

                    call.respondRedirect("http://${minikubeIpAddress}:$nodePort")
                }
            }
        }

        // One of these are called. It does not seem obvious which one.
        route("${KubernetesCompute.baseContext}/app-authorization") {
            handleAppAuthorization(this, this@WebService)
        }
        route("${KubernetesCompute.baseContext}/app-authorization/{...}") {
            handleAppAuthorization(this, this@WebService)
        }
        route("${KubernetesCompute.baseContext}/app-authorization/") {
            handleAppAuthorization(this, this@WebService)
        }
    }

    // Called by envoy before every single request. We are allowed to control the "Cookie" header.
    private fun handleAppAuthorization(
        route: Route,
        webService: WebService,
    ) {
        route.handle {
            val host = call.request.host()
            log.info("Authorizing request: $host")

            if (ingressCache.get(host) != null) {
                call.respondText("", status = HttpStatusCode.OK)
                return@handle
            }

            if (!host.startsWith(prefix) || !host.endsWith(domain)) {
                call.respondText("Forbidden", status = HttpStatusCode.Forbidden)
                return@handle
            }

            val jobId = host.removePrefix(prefix).removeSuffix(".$domain").substringBeforeLast('-')
            val rank = host.removePrefix(prefix).removeSuffix(".$domain").substringAfterLast('-').toInt()
            if (webService.authorizeUser(call, JobIdAndRank(jobId, rank))) {
                val requestCookies = HashMap(call.request.cookies.rawCookies).apply {
                    // Remove authentication tokens
                    remove(cookieName)
                    remove("refreshToken")
                }
                call.response.header(
                    HttpHeaders.Cookie,
                    requestCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                )

                call.respondText("", status = HttpStatusCode.OK)
            }
        }
    }

    private suspend fun authorizeUser(call: ApplicationCall, jobIdAndRank: JobIdAndRank): Boolean {
        val sessionId = call.request.cookies[cookieName] ?: run {
            call.respondText(status = HttpStatusCode.Forbidden) { "Unauthorized." }
            return false
        }

        if (sessionCache.get(sessionId) != jobIdAndRank) {
            call.respondText(status = HttpStatusCode.Forbidden) { "Unauthorized." }
            return false
        }

        return true
    }

    companion object : Loggable {
        override val log = logger()
    }
}
