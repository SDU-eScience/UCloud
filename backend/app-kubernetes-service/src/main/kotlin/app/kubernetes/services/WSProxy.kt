package dk.sdu.cloud.app.kubernetes.services

import io.ktor.client.features.websocket.ws
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.request.header
import io.ktor.websocket.DefaultWebSocketServerSession
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

suspend fun DefaultWebSocketServerSession.runWSProxy(
    tunnel: Tunnel,
    uri: String = "/",
    cookies: Map<String, String> = emptyMap()
) {
    val clientConn = this
    webSocketClient.ws(
        method = HttpMethod.Get,
        host = tunnel.ipAddress,
        port = tunnel.localPort,
        path = uri,
        request = {
            // We must use the same protocol and extensions for the proxying to work.
            val protocol = clientConn.call.request.header(HttpHeaders.SecWebSocketProtocol)
            if (protocol != null) {
                header(HttpHeaders.SecWebSocketProtocol, protocol)
            }

            val extensions = clientConn.call.request.header(HttpHeaders.SecWebSocketExtensions)
            if (extensions != null) {
                header(HttpHeaders.SecWebSocketExtensions, extensions)
            }

            // Must add an origin for the remote server to trust us
            header(HttpHeaders.Origin, "http://${tunnel.ipAddress}:${tunnel.localPort}")

            header(
                HttpHeaders.Cookie,
                cookies.entries.joinToString(";") { "${it.key}=${it.value}" }
            )
        }) {
        val serverConn = this
        val clientToServer = launch {
            try {
                while (true) {
                    val frame = clientConn.incoming.receive()
                    serverConn.outgoing.send(frame)
                }
            } catch (ex: ClosedReceiveChannelException) {
                log.debug("Closing channel (Client ==> Server)")
            }
        }

        val serverToClient = launch {
            try {
                while (true) {
                    val frame = serverConn.incoming.receive()
                    clientConn.outgoing.send(frame)
                }
            } catch (ex: ClosedReceiveChannelException) {
                log.debug("Closing channel (Server ==> Client)")
            }
        }

        clientToServer.join()
        serverToClient.join()
    }
}

private val log = LoggerFactory.getLogger("dk.sdu.cloud.app.kubernetes.services.WSProxy")
