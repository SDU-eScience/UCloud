package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.Ok
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.model.StoragePath
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class IRodsController(private val storageService: StorageConnectionFactory) {
    fun configure(routing: Route) = with(routing) {
        route("files") {
            implement(FileDescriptions.listAtPath) { request ->
                logEntry(log, request)
                if (!protect()) return@implement

                val principal = call.request.validatedPrincipal
                val connection =
                    storageService.createForAccount(principal.subject, principal.token).capture() ?: run {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@implement
                    }

                connection.use {
                    val listAt = connection.fileQuery.listAt(connection.parsePath(request.path))
                    when (listAt) {
                        is Ok -> ok(listAt.result)
                        is Error -> {
                            error(CommonErrorMessage(listAt.message), HttpStatusCode.InternalServerError)
                        }
                    }
                }

            }
        }
    }

    private fun StorageConnection.parsePath(pathFromRequest: String): StoragePath =
        paths.parseAbsolute(pathFromRequest, addHost = true)

    companion object {
        private val log = LoggerFactory.getLogger(IRodsController::class.java)
    }
}