package dk.sdu.cloud.app.kubernetes.services

import io.ktor.client.features.websocket.ws
import io.ktor.client.request.header
import io.ktor.http.HttpMethod
import io.ktor.request.header
import io.ktor.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.launch

suspend fun DefaultWebSocketServerSession.runWSProxy(
    tunnel: Tunnel,
    path: String = "/"
) {
    val clientConn = this
    webSocketClient.ws(
        method = HttpMethod.Get,
        host = "127.0.0.1",
        port = tunnel.localPort,
        path = path,
        request = {
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
            header("Origin", "http://127.0.0.1:${tunnel.localPort}")
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
