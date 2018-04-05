package dk.sdu.cloud.storage.http

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.MetadataEntry
import dk.sdu.cloud.storage.api.StoragePath
import dk.sdu.cloud.storage.services.ICATService
import dk.sdu.cloud.storage.services.ext.StorageConnection
import dk.sdu.cloud.storage.services.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.services.ext.StorageException
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class FilesController(
    private val storageService: StorageConnectionFactory,
    private val icatService: ICATService
) {
    private suspend fun RESTHandler<*, *, CommonErrorMessage>.error(message: String, statusCode: HttpStatusCode) {
        error(CommonErrorMessage(message), statusCode)
    }

    private suspend inline fun RESTHandler<*, *, CommonErrorMessage>.withIRodsConnection(
        body: (StorageConnection) -> Unit
    ) {
        if (!protect()) return

        val principal = call.request.validatedPrincipal
        return try {
            storageService.createForAccount(principal.subject, principal.token).use { body(it) }
        } catch (ex: StorageException) {
            when (ex) {
                is StorageException.BadAuthentication -> error("Unauthorized", HttpStatusCode.Unauthorized)
                is StorageException.Duplicate -> error("Item already exists", HttpStatusCode.Conflict)
                is StorageException.BadPermissions -> error("Not allowed", HttpStatusCode.Forbidden)
                is StorageException.NotFound -> error("Not found", HttpStatusCode.NotFound)
                is StorageException.NotEmpty -> error("Item is not empty", HttpStatusCode.Forbidden)

                is StorageException.BadConnection -> {
                    log.warn("Unable to connect to iRODS backend")
                    log.warn(ex.stackTraceToString())

                    error("Internal Server Error", HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    private suspend fun RESTHandler<*, *, CommonErrorMessage>.parsePath(
        connection: StorageConnection,
        path: String
    ): StoragePath? {
        return try {
            connection.parsePath(path)
        } catch (ex: IllegalArgumentException) {
            error(CommonErrorMessage("Illegal path"), HttpStatusCode.BadRequest)
            return null
        }
    }

    fun configure(routing: Route) = with(routing) {
        route("files") {
            implement(FileDescriptions.listAtPath) { request ->
                logEntry(log, request)

                withIRodsConnection { connection ->
                    val path = parsePath(connection, request.path) ?: return@implement
                    ok(connection.fileQuery.listAt(path))
                }
            }

            implement(FileDescriptions.stat) { request ->
                logEntry(log, request)

                withIRodsConnection { connection ->
                    val path = parsePath(connection, request.path) ?: return@implement
                    ok(connection.fileQuery.stat(path))
                }
            }

            implement(FileDescriptions.markAsFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                withIRodsConnection { connection ->
                    val path = parsePath(connection, req.path) ?: return@implement

                    return@implement try {
                        connection.metadata.updateMetadata(
                            path,
                            listOf(MetadataEntry(favoriteKey(call.request.validatedPrincipal), "true")),
                            emptyList()
                        )
                        ok(Unit)
                    } catch (ex: Exception) {
                        error(
                            CommonErrorMessage("An error has occurred"),
                            HttpStatusCode.InternalServerError
                        )
                    }
                }
            }

            implement(FileDescriptions.removeFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement


                withIRodsConnection { connection ->
                    val path = parsePath(connection, req.path) ?: return@implement

                    val favKey = favoriteKey(call.request.validatedPrincipal)
                    return@implement try {
                        connection.metadata.updateMetadata(
                            path,
                            emptyList(),
                            listOf(
                                MetadataEntry(favKey, "true"),
                                MetadataEntry(favKey, "false")
                            )
                        )

                        ok(Unit)
                    } catch (ex: Exception) {
                        error(
                            CommonErrorMessage("An error has occurred"),
                            HttpStatusCode.InternalServerError
                        )
                    }
                }
            }

            implement(FileDescriptions.createDirectory) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                if (call.request.principalRole in setOf(Role.ADMIN, Role.SERVICE) && req.owner != null) {
                    log.debug("Authenticated as a privileged account. Using direct strategy")
                    val success = icatService.createDirectDirectory(req.path, req.owner)

                    if (success) ok(Unit)
                    else error(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
                } else {
                    log.debug("Authenticated as a normal user. Using Jargon strategy")
                    withIRodsConnection { connection ->
                        val path = parsePath(connection, req.path) ?: return@implement

                        if (connection.fileQuery.exists(path)) {
                            error(CommonErrorMessage("File already exists!"), HttpStatusCode.Conflict)
                        } else {
                            try {
                                connection.files.createDirectory(path, false)
                                ok(Unit)
                            } catch (ex: Exception) {
                                error(CommonErrorMessage("Could not create directory"), HttpStatusCode.BadRequest)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun favoriteKey(who: DecodedJWT): String {
        return FAVORITE_KEY_PREFIX + "_" + who.subject
    }

    private fun StorageConnection.parsePath(pathFromRequest: String): StoragePath =
        paths.parseAbsolute(pathFromRequest, isMissingZone = true)

    companion object {
        private val log = LoggerFactory.getLogger(FilesController::class.java)
        private const val FAVORITE_KEY_PREFIX = "favorited"
    }
}