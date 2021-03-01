package dk.sdu.cloud.calls.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.curl.*
import io.ktor.client.features.websocket.*
import io.ktor.util.*

@OptIn(KtorExperimentalAPI::class)
@ThreadLocal
actual val httpClient = HttpClient(Curl)

@OptIn(KtorExperimentalAPI::class)
@ThreadLocal
actual val websocketClient = HttpClient(CIO) {
    install(WebSockets)
}