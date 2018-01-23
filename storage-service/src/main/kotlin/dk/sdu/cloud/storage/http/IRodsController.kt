package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.safeJobId
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.Ok
import dk.sdu.cloud.storage.Server.Companion.StorageSession
import dk.sdu.cloud.storage.api.ACLDescriptions
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.GroupDescriptions
import dk.sdu.cloud.storage.api.UserDescriptions
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.model.StoragePath
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class IRodsController {
    fun configure(routing: Route) = with(routing) {
        route("files") {
            implement(FileDescriptions.listAtPath) {
                logEntry(log, it)
                val connection = call.attributes[StorageSession]
                val listAt = connection.fileQuery.listAt(connection.parsePath(it.path))
                when (listAt) {
                    is Ok -> ok(listAt.result)
                    is Error -> {
                        error(CommonErrorMessage(listAt.message), HttpStatusCode.InternalServerError)
                    }
                }
            }
        }

        route("acl") {
            implement(ACLDescriptions.listAtPath) {
                logEntry(log, it)
                val connection = call.attributes[StorageSession]
                val listAt = connection.accessControl.listAt(connection.parsePath(it.path))
                when (listAt) {
                    is Ok -> ok(listAt.result)
                    is Error -> {
                        // TODO We have no idea with these error types
                        error(CommonErrorMessage(listAt.message), HttpStatusCode.InternalServerError)
                    }
                }
            }
        }

        route("users") {
            implement(UserDescriptions.findByName) {
                logEntry(log, it)
                if (!protect(listOf(Role.ADMIN))) {
                    log.info("User is not authorized for ${call.request.safeJobId}")
                    return@implement
                }

                if (call.request.principalRole != Role.ADMIN) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@implement
                }
                val connection = call.attributes[StorageSession]
                val admin = connection.userAdmin ?: return@implement run {
                    error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                }

                val findBy = admin.findByUsername(it.name)
                when (findBy) {
                    is Ok -> ok(findBy.result)
                    is Error -> error(CommonErrorMessage(findBy.message), HttpStatusCode.InternalServerError)
                }
            }
        }

        route("groups") {
            implement(GroupDescriptions.findByName) {
                logEntry(log, it)
                if (!protect(listOf(Role.ADMIN))) {
                    log.info("User is not authorized for ${call.request.safeJobId}")
                    return@implement
                }

                val connection = call.attributes[StorageSession]
                try {
                    val listAt = connection.groups.listGroupMembers(it.name)
                    when (listAt) {
                        is Ok -> ok(listAt.result)
                        is Error -> error(
                            CommonErrorMessage(listAt.message),
                            HttpStatusCode.InternalServerError
                        )
                    }
                } catch (ex: Exception) {
                    // TODO Not sure if listGroupMembers will throw exceptions
                    error(CommonErrorMessage("Internal error"), HttpStatusCode.InternalServerError)
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