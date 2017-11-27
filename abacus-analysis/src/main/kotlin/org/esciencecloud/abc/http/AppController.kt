package org.esciencecloud.abc.http

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import org.esciencecloud.abc.services.ApplicationDAO

class AppController(private val source: ApplicationDAO) {
    fun configure(routing: Route) = with(routing) {
        get("app/{name}/{version?}") {
            val name = call.parameters["name"]!!
            val version = call.parameters["version"]

            if (version == null) {
                val all = source.findAllByName(name)
                if (all.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, all)
                } else {
                    call.respond(all)
                }
            } else {
                val app = source.findByNameAndVersion(name, version)
                if (app == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(app)
                }
            }
        }
    }
}