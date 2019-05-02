package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.api.AppKubernetesDescriptions
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.websocket.ws
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.request.header
import io.ktor.routing.Route
import io.ktor.websocket.webSocket
import kotlinx.coroutines.launch

class VncService(
    private val podService: PodService
) {
    private val client = HttpClient(CIO).config {
        install(io.ktor.client.features.websocket.WebSockets)
    }

    fun install(routing: Route): Unit = with(routing) {
        webSocket("${AppKubernetesDescriptions.baseContext}/vnc/:id") {
            call.parameters
            call.request.queryParameters
            val clientConn = this
            client.ws(method = HttpMethod.Get, host = "localhost", port = 5900, path = "/", request = {
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

        return@with
    }

    private fun createTunnel(incomingId: String): Tunnel {
        val jobId = incomingId // Slightly less secure, but should work for prototype
        // TODO Might want to be flexible in which port we accept this from
        return podService.createTunnel(jobId, 5900)
    }
}
