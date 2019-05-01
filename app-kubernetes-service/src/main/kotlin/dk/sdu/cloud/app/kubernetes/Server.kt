package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.kubernetes.rpc.AppKubernetesController
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.websocket.ws
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.request.header
import io.ktor.routing.routing
import io.ktor.websocket.WebSockets
import io.ktor.websocket.webSocket
import kotlinx.coroutines.launch

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val podService = PodService(DefaultKubernetesClient(), serviceClient)

        podService.initializeListeners()


        with(micro.server) {
            configureControllers(
                AppKubernetesController(podService)
            )
        }

        /*
        val ktorEngine = micro.feature(ServerFeature).ktorApplicationEngine!!

        val client = HttpClient(CIO).config {
            install(io.ktor.client.features.websocket.WebSockets, configure = {

            })
        }

        ktorEngine.application.install(WebSockets)
        ktorEngine.application.routing {
            webSocket {
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
        }
        */

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
