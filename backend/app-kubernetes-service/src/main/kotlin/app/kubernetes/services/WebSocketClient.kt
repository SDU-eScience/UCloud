package dk.sdu.cloud.app.kubernetes.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

val webSocketClient = HttpClient(CIO).config {
    install(io.ktor.client.features.websocket.WebSockets)
}
