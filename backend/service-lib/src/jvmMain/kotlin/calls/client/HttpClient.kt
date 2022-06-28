package dk.sdu.cloud.calls.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import java.net.URLEncoder

internal fun createHttpClient() = HttpClient(CIO) {
    expectSuccess = false

    engine {
        requestTimeout = 1000 * 60 * 5
    }
}

fun createWebsocketClient(): HttpClient = HttpClient(CIO) {
    install(WebSockets)
    expectSuccess = false
}

actual fun urlEncode(value: String): String {
    return URLEncoder.encode(value, "UTF-8")
}