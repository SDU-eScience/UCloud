package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.Result
import dk.sdu.cloud.storage.api.ACLDescriptions
import dk.sdu.cloud.storage.api.TemporaryRight
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.ext.irods.IRodsUser
import dk.sdu.cloud.storage.model.AccessEntry
import dk.sdu.cloud.storage.model.AccessRight
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class ACLController(private val storageService: StorageConnectionFactory) {
    fun configure(routing: Route) = with(routing) {
        route("acl") {
            protect()

            implement(ACLDescriptions.grantRights) { req ->
                logEntry(log, req)

                val principal = call.request.validatedPrincipal
                val connection =
                    storageService.createForAccount(principal.subject, principal.token).capture() ?: run {
                        error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                        return@implement
                    }

                connection.use {
                    val path = connection.paths.parseAbsolute(req.onFile, true)
                    val entity = IRodsUser.fromUsernameAndZone(req.entity, connection.connectedUser.zone)

                    log.debug("Granting permissions: $req")

                    val irodsPermission = when (req.rights) {
                        TemporaryRight.READ -> AccessRight.READ
                        TemporaryRight.READ_WRITE -> AccessRight.READ_WRITE
                        TemporaryRight.OWN -> AccessRight.OWN
                    }

                    val accessEntry = AccessEntry(entity, irodsPermission)
                    connection.accessControl.updateACL(path, listOf(accessEntry), false).capture() ?: run {
                        val error = Result.lastError<Unit>()
                        log.info("Caught an error while updating ACL: ${error.message}")
                        error(CommonErrorMessage(error.message), HttpStatusCode.BadRequest)
                        return@implement
                    }
                }

                ok(Unit)
            }

            implement(ACLDescriptions.revokeRights) { req ->
                logEntry(log, req)

                val principal = call.request.validatedPrincipal
                val connection =
                    storageService.createForAccount(principal.subject, principal.token).capture() ?: run {
                        error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                        return@implement
                    }

                connection.use {
                    val path = connection.paths.parseAbsolute(req.onFile, true)
                    val entity = IRodsUser.fromUsernameAndZone(req.entity, connection.connectedUser.zone)
                    log.debug("Removing permissions $req")
                    val accessEntry = AccessEntry(entity, AccessRight.NONE)
                    connection.accessControl.updateACL(path, listOf(accessEntry), false).capture() ?: run {
                        val error = Result.lastError<Unit>()
                        log.info("Caught an error while updating ACL: ${error.message}")
                        error(CommonErrorMessage(error.message), HttpStatusCode.BadRequest)
                        return@implement
                    }
                }

                ok(Unit)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FilesController::class.java)
    }
}