package dk.sdu.cloud.service

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.calls.server.JobId
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.XForwardedHeaderSupport
import io.ktor.http.HttpHeaders
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.server.engine.ApplicationEngine

fun Application.installDefaultFeatures() {
    // Default ktor features
    install(DefaultHeaders)
    install(XForwardedHeaderSupport)
    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()

            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            configure(JsonParser.Feature.ALLOW_COMMENTS, true)
            configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
            configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true)
        }
    }

    install(CallLogging) {
        mdc("request-id") { call ->
            call.request.header(HttpHeaders.JobId)
        }
    }
}


typealias HttpServerProvider = (Application.() -> Unit) -> ApplicationEngine

