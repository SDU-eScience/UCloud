package dk.sdu.cloud.calls.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.curl.*
import io.ktor.client.features.websocket.*
import io.ktor.util.*

actual val httpClient = HttpClient(Curl)
@OptIn(KtorExperimentalAPI::class)
actual val websocketClient = HttpClient(CIO) {
    install(WebSockets)
}