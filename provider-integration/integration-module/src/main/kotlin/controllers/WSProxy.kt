package dk.sdu.cloud.controllers

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

val webSocketClient = HttpClient(CIO) {
    install(WebSockets)
    expectSuccess = false
    engine {
        requestTimeout = 0
    }
}

suspend fun WebSocketServerSession.runWSProxy(
    ipAddress: String,
    port: Int,
    uri: String = "/",
    cookies: Map<String, String> = emptyMap()
) {
    val clientConn = this
    webSocketClient.ws(
        method = HttpMethod.Get,
        host = ipAddress,
        port = port,
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
            header(HttpHeaders.Origin, "http://${ipAddress}:${port}")

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

private val log = LoggerFactory.getLogger("dk.sdu.cloud.controllers.WSProxy")
