package org.esciencecloud.abc.http

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.routing.Route
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.esciencecloud.abc.api.HPCApplications
import org.esciencecloud.abc.services.ApplicationDAO
import org.esciencecloud.client.GatewayJobResponse
import org.esciencecloud.client.implement

class AppController(private val source: ApplicationDAO) {
    fun configure(routing: Route) = with(routing) {
        implement(HPCApplications.FindByNameAndVersion.description) {
            val result = source.findByNameAndVersion(it.name, it.version)

            if (result == null) error("Not found", HttpStatusCode.NotFound)
            else ok(result)
        }

        implement(HPCApplications.FindAllByName.description) {
            val result = source.findAllByName(it.name)

            if (result.isEmpty()) error(emptyList(), HttpStatusCode.NotFound)
            else ok(result)
        }

        implement(HPCApplications.ListAll.description) {
            ok(source.all())
        }

        implement(HPCApplications.Test.description) {
            ok(it)
        }

        implement(HPCApplications.AppRequest.Start.description) {
            println(it)
            ok(GatewayJobResponse.started("123", 1L, 1, 1L))
        }
    }
}

fun main(args: Array<String>) {
    embeddedServer(CIO, port = 8080) {
        install(CallLogging)
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
            }
        }

        routing {
            route("/hpc/apps") {
                AppController(ApplicationDAO).configure(this)
            }
        }
    }.start(wait = true)
}