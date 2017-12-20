package dk.sdu.cloud.abc.services

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.jackson.jackson
import io.ktor.routing.Routing
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import java.util.concurrent.TimeUnit

class HTTPServer(val hostname: String, val port: Int) {
    private var server: ApplicationEngine? = null
    fun start(wait: Boolean = false, visitor: Routing.() -> Unit) {
        if (server != null) throw IllegalStateException("Already initialized")
        server = embeddedServer(CIO, port = port) {
            install(CallLogging)
            install(DefaultHeaders)
            install(ContentNegotiation) {
                jackson { registerKotlinModule() }
            }

            routing { visitor() }
        }.start(wait)
    }

    fun stop() {
        server!!.stop(0, 5, TimeUnit.SECONDS)
    }
}