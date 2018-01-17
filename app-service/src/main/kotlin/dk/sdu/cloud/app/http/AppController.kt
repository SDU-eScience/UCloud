package dk.sdu.cloud.app.http

import dk.sdu.cloud.app.api.HPCApplicationDescriptions
import dk.sdu.cloud.app.services.ApplicationDAO
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class AppController(private val source: ApplicationDAO) {
    fun configure(routing: Route) = with(routing) {
        route("apps") {
            implement(HPCApplicationDescriptions.findByNameAndVersion) {
                logEntry(log, it)

                val result = source.findByNameAndVersion(it.name, it.version)

                if (result == null) error("Not found", HttpStatusCode.NotFound)
                else ok(result)
            }

            implement(HPCApplicationDescriptions.findByName) {
                logEntry(log, it)

                val result = source.findAllByName(it.name)

                if (result.isEmpty()) error(emptyList(), HttpStatusCode.NotFound)
                else ok(result)
            }

            implement(HPCApplicationDescriptions.listAll) {
                logEntry(log, it)

                ok(source.all())
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AppController::class.java)
    }
}
