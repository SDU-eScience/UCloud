package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.api.ApplicationDescriptions
import dk.sdu.cloud.app.api.QueryInternalVncParametersResponse
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.request.header
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.websocket.webSocket
import kotlinx.coroutines.launch

class VncService(
    private val podService: PodService
) {
    private val client = HttpClient(CIO).config {
        install(io.ktor.client.features.websocket.WebSockets)
    }

    private val jobIdToJob = HashMap<String, VerifiedJob>()

    fun install(routing: Route): Unit = with(routing) {
        routing.application.install(io.ktor.websocket.WebSockets)

        webSocket("${AppKubernetesDescriptions.baseContext}/vnc/{id}") {
            val id = call.parameters["id"] ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            val tunnel = createTunnel(id)

            tunnel.use {
                val clientConn = this
                client.ws(method = HttpMethod.Get, host = "localhost", port = tunnel.localPort, path = "/", request = {
                    // We must use the same protocol and extensions for the proxying to work.
                    val protocol = clientConn.call.request.header("Sec-WebSocket-Protocol")
                    if (protocol != null) {
                        header("Sec-WebSocket-Protocol", protocol)
                    }

                    val extensions = clientConn.call.request.header("Sec-WebSocket-Extensions")
                    if (extensions != null) {
                        header("Sec-WebSocket-Extensions", extensions)
                    }

                    // Must add an origin for the remote server to trust us
                    header("Origin", "http://localhost:8000")
                }) {
                    val serverConn = this
                    val clientToServer = launch {
                        while (true) {
                            val frame = clientConn.incoming.receive()
                            serverConn.outgoing.send(frame)
                        }
                    }

                    val serverToClient = launch {
                        while (true) {
                            val frame = serverConn.incoming.receive()
                            clientConn.outgoing.send(frame)
                        }
                    }

                    clientToServer.join()
                    serverToClient.join()
                }
            }
        }

        return@with
    }

    fun queryParameters(job: VerifiedJob): QueryInternalVncParametersResponse {
        jobIdToJob[job.id] = job
        return QueryInternalVncParametersResponse(
            "${AppKubernetesDescriptions.baseContext}/vnc/${job.id}",
            job.application.invocation.vnc?.password
        )
    }

    private suspend fun createTunnel(incomingId: String): Tunnel {
        val jobId = incomingId // Slightly less secure, but should work for prototype
        val job = jobIdToJob[jobId] ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return podService.createTunnel(jobId, job.application.invocation.vnc?.port ?: 5900)
    }
}
