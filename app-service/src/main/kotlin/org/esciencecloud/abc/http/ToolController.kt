package org.esciencecloud.abc.http

import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.esciencecloud.abc.api.HPCToolDescriptions
import org.esciencecloud.abc.api.StandardError
import org.esciencecloud.abc.services.ToolDAO
import org.esciencecloud.service.implement

class ToolController(private val source: ToolDAO) {
    fun configure(routing: Route) = with(routing) {
        route("tools") {
            implement(HPCToolDescriptions.findByName) {
                val result = source.findAllByName(it.name)
                if (result.isEmpty()) error(StandardError("Not found"), HttpStatusCode.NotFound)
                else ok(result)
            }

            implement(HPCToolDescriptions.findByNameAndVersion) {
                val result = source.findByNameAndVersion(it.name, it.version)
                if (result == null) error(StandardError("Not found"), HttpStatusCode.NotFound)
                else ok(result)
            }

            implement(HPCToolDescriptions.listAll) {
                ok(source.all())
            }
        }
    }
}
