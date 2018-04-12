package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.FileDescriptions
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class FilesController {
    fun configure(routing: Route) = with(routing) {
        route("files") {
            implement(FileDescriptions.listAtPath) { request ->
                logEntry(log, request)

                TODO()
            }

            implement(FileDescriptions.stat) { request ->
                logEntry(log, request)

                TODO()
            }

            implement(FileDescriptions.markAsFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement
                TODO()
            }

            implement(FileDescriptions.removeFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                TODO()
            }

            implement(FileDescriptions.createDirectory) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                if (call.request.principalRole in setOf(Role.ADMIN, Role.SERVICE) && req.owner != null) {
                    log.debug("Authenticated as a privileged account. Using direct strategy")

                    TODO()
                } else {
                    log.debug("Authenticated as a normal user. Using Jargon strategy")
                    TODO()
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FilesController::class.java)
    }
}