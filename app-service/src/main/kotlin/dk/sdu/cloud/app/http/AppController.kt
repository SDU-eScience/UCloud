package dk.sdu.cloud.app.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.ApplicationWithOptionalDependencies
import dk.sdu.cloud.app.api.HPCApplicationDescriptions
import dk.sdu.cloud.app.services.ApplicationDAO
import dk.sdu.cloud.app.services.ToolDAO
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

                val app = source.findByNameAndVersion(it.name, it.version) ?: return@implement error(
                    CommonErrorMessage("Not found"),
                    HttpStatusCode.NotFound
                )

                val tool = if (it.resolve == true) {
                    ToolDAO.findByNameAndVersion(app.tool.name, app.tool.version) ?: return@implement run {
                        log.warn("Could not resolve tool dependency for application: $app!")
                        error(
                            CommonErrorMessage("Internal server error"),
                            HttpStatusCode.InternalServerError
                        )
                    }
                } else {
                    null
                }

                assert(it.resolve != true || tool != null)
                ok(ApplicationWithOptionalDependencies(app, tool))
            }

            implement(HPCApplicationDescriptions.findByName) {
                logEntry(log, it)

                val result = source.findAllByName(it.name)

                if (result.isEmpty()) error(CommonErrorMessage("Not found"), HttpStatusCode.NotFound)
                else ok(result)
            }

            implement(HPCApplicationDescriptions.listAll) {
                logEntry(log, it)

                ok(source.all().map { it.toSummary() })
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AppController::class.java)
    }
}
