package dk.sdu.cloud.calls.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.websocket.*
import io.ktor.util.*
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.net.ConnectException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

internal fun createHttpClient() = HttpClient(OkHttp) {
    expectSuccess = false

    engine {
        config {
            readTimeout(5, TimeUnit.MINUTES)
            writeTimeout(5, TimeUnit.MINUTES)
            dispatcher(Dispatcher().apply {
                maxRequests = 10_000
                maxRequestsPerHost = 10_000
            })

            // All of our connections are going to the same address (cloud.sdu.dk). So we keep a large pool
            // ready with a large timeout. (This doesn't apply to local dev but this won't break anything)
            connectionPool(ConnectionPool(128, 30, TimeUnit.MINUTES))
        }
    }
}

@OptIn(KtorExperimentalAPI::class)
fun createWebsocketClient(): HttpClient = HttpClient(CIO) {
    install(WebSockets)
    expectSuccess = false
}

actual fun urlEncode(value: String): String {
    return URLEncoder.encode(value, "UTF-8")
}