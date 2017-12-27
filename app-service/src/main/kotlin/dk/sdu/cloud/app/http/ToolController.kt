package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.HPCToolDescriptions
import dk.sdu.cloud.app.services.ToolDAO
import dk.sdu.cloud.service.implement
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route

class ToolController(private val source: ToolDAO) {
    fun configure(routing: Route) = with(routing) {
        route("tools") {
            implement(HPCToolDescriptions.findByName) {
                val result = source.findAllByName(it.name)
                if (result.isEmpty()) error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                else ok(result)
            }

            implement(HPCToolDescriptions.findByNameAndVersion) {
                val result = source.findByNameAndVersion(it.name, it.version)
                if (result == null) error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                else ok(result)
            }

            implement(HPCToolDescriptions.listAll) {
                ok(source.all())
            }
        }
    }
}
