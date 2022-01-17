package dk.sdu.cloud.calls.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.curl.*
import io.ktor.client.features.websocket.*
import io.ktor.util.*

@OptIn(KtorExperimentalAPI::class)
actual fun createHttpClient(): HttpClient {
    return HttpClient(Curl) {
        expectSuccess = false
    }
}

@OptIn(KtorExperimentalAPI::class)
actual fun createWebsocketClient(): HttpClient = HttpClient(CIO) {
    install(WebSockets)
    expectSuccess = false
}