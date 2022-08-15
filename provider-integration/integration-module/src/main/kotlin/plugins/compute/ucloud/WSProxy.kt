package dk.sdu.cloud.plugins.compute.ucloud

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.request.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

val webSocketClient = HttpClient(CIO).config {
    install(WebSockets)
    expectSuccess = false
}

suspend fun WebSocketServerSession.runWSProxy(
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

            if (cookies.entries.isNotEmpty()) {
                header(
                    HttpHeaders.Cookie,
                    cookies.entries.joinToString(";") { "${it.key}=${it.value}" }
                )
            }
        }) {
        val serverConn = this

        // NOTE(Dan): Client refers to the application and server refers to UCloud
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
