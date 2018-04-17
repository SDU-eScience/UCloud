package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.ACLDescriptions
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class ACLController{
    fun configure(routing: Route) = with(routing) {
        route("acl") {
            protect()

            implement(ACLDescriptions.grantRights) { req ->
                logEntry(log, req)

                val principal = call.request.validatedPrincipal

                TODO()
            }

            implement(ACLDescriptions.revokeRights) { req ->
                logEntry(log, req)

                val principal = call.request.validatedPrincipal
                TODO()
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FilesController::class.java)
    }
}