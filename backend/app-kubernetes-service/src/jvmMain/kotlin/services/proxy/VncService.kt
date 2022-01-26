package dk.sdu.cloud.app.kubernetes.services.proxy

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.kubernetes.api.KubernetesCompute
import dk.sdu.cloud.app.kubernetes.services.*
import dk.sdu.cloud.app.orchestrator.api.InteractiveSessionType
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.OpenSession
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.webSocket
import kotlinx.serialization.encodeToString

class VncService(
    private val db: DBContext,
    private val sessions: SessionDao,
    private val jobCache: VerifiedJobCache,
    private val resources: ResourceCache,
    private val tunnelManager: TunnelManager,
) {
    // Relatively low maxAge to make sure that we renew the session id regularly
    private val sessionCache = SimpleCache<String, JobIdAndRank>(maxAge = 60_000 * 15L) { sessionId ->
        sessions.findSessionOrNull(db, sessionId, InteractiveSessionType.WEB)
    }

    suspend fun createSession(jobAndRank: JobAndRank): OpenSession.Vnc {
        val sessionId = sessions.createSession(db, jobAndRank, InteractiveSessionType.VNC)
        sessionCache.insert(sessionId, JobIdAndRank(jobAndRank.job.id, jobAndRank.rank))

        val job = jobCache.findJob(jobAndRank.job.id) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val resources = resources.findResources(job)
        val password = resources.application.invocation.vnc?.password

        return OpenSession.Vnc(
            jobAndRank.job.id,
            jobAndRank.rank,
            "${KubernetesCompute.baseContext}/vnc/$sessionId",
            password
        )
    }

    fun install(routing: Route): Unit = with(routing) {
        webSocket("${KubernetesCompute.baseContext}/vnc/{id}", protocol = "binary") {
            val sessionId = call.parameters["id"] ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            val jobAndRank = sessionCache.get(sessionId)
                ?: run {
                    call.respondText(
                        defaultMapper.encodeToString(
                            CommonErrorMessage("Unable to connect to remote desktop. Try to reload the page.")
                        ),
                        ContentType.Application.Json,
                        io.ktor.http.HttpStatusCode.BadRequest
                    )
                    return@webSocket
                }

            log.info("Incoming VNC connection for application $jobAndRank")
            val tunnel = createTunnel(jobAndRank.jobId, jobAndRank.rank)

            tunnel.use {
                runWSProxy(tunnel, uri = "/websockify")
            }
        }
        return@with
    }

    private suspend fun createTunnel(jobId: String, rank: Int): Tunnel {
        val job = jobCache.findJob(jobId) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val resources = resources.findResources(job)
        val port = resources.application.invocation.vnc?.port ?: 5900
        return tunnelManager.createOrUseExistingTunnel(jobId, port, rank)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
