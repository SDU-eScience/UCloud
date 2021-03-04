package dk.sdu.cloud.service

import dk.sdu.cloud.calls.server.JobId
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.http.HttpHeaders
import io.ktor.request.header
import io.ktor.server.engine.ApplicationEngine

fun Application.installDefaultFeatures() {
    // Default ktor features
    install(DefaultHeaders)
    install(XForwardedHeaderSupport)

    install(CallLogging) {
        mdc("request-id") { call ->
            call.request.header(HttpHeaders.JobId)
        }
    }
}


typealias HttpServerProvider = (Application.() -> Unit) -> ApplicationEngine

