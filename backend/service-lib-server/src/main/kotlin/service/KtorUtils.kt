package dk.sdu.cloud.service

import dk.sdu.cloud.calls.server.JobId
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.request.*

fun Application.installDefaultFeatures() {
    // Default ktor features
    install(DefaultHeaders)
    install(XForwardedHeaders)
    install(Compression)

    install(CallLogging) {
        mdc("request-id") { call ->
            call.request.header(HttpHeaders.JobId)
        }
    }
}

typealias HttpServerProvider = (port: Int, Application.() -> Unit) -> ApplicationEngine

