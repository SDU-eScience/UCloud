package dk.sdu.cloud.storage.http

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.Ok
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.model.MetadataEntry
import dk.sdu.cloud.storage.model.StoragePath
import dk.sdu.cloud.storage.services.ICATService
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class FilesController(
    private val storageService: StorageConnectionFactory,
    private val icatService: ICATService
) {
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
                    val path = try {
                        connection.parsePath(request.path)
                    } catch (ex: IllegalArgumentException) {
                        error(CommonErrorMessage("Illegal path"), HttpStatusCode.BadRequest)
                        return@implement
                    }

                    val listAt = connection.fileQuery.listAt(path)
                    when (listAt) {
                        is Ok -> ok(listAt.result)
                        is Error -> {
                            val code = when (listAt.errorCode) {
                                1 -> HttpStatusCode.NotFound
                                4 -> HttpStatusCode.BadRequest
                                else -> HttpStatusCode.InternalServerError
                            }
                            error(CommonErrorMessage(listAt.message), code)
                        }
                    }
                }
            }

            implement(FileDescriptions.markAsFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                val principal = call.request.validatedPrincipal
                val connection =
                    storageService.createForAccount(principal.subject, principal.token).capture() ?: run {
                        error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                        return@implement
                    }

                connection.use {
                    val path = try {
                        connection.paths.parseAbsolute(req.path, true)
                    } catch (ex: Exception) {
                        return@implement error(CommonErrorMessage("Bad input path"), HttpStatusCode.BadRequest)
                    }

                    return@implement try {
                        connection.metadata.updateMetadata(
                            path,
                            listOf(MetadataEntry(favoriteKey(principal), "true")),
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

                val principal = call.request.validatedPrincipal
                val connection =
                    storageService.createForAccount(principal.subject, principal.token).capture() ?: run {
                        error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                        return@implement
                    }

                connection.use {
                    val path = try {
                        connection.paths.parseAbsolute(req.path, true)
                    } catch (ex: Exception) {
                        return@implement error(CommonErrorMessage("Bad input path"), HttpStatusCode.BadRequest)
                    }

                    val favKey = favoriteKey(principal)
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
                    val principal = call.request.validatedPrincipal
                    val connection =
                        storageService.createForAccount(principal.subject, principal.token).capture() ?: run {
                            error(CommonErrorMessage("Unauthorized"), HttpStatusCode.Unauthorized)
                            return@implement
                        }

                    connection.use {
                        val path = try {
                            connection.paths.parseAbsolute(req.path, true)
                        } catch (ex: Exception) {
                            return@implement error(CommonErrorMessage("Bad input path"), HttpStatusCode.BadRequest)
                        }

                        val exists = it.fileQuery.stat(path)
                        if (exists !is Ok) {
                            try {
                                it.files.createDirectory(path, false)
                                ok(Unit)
                            } catch (ex: Exception) {
                                error(CommonErrorMessage("Could not create directory"), HttpStatusCode.BadRequest)
                            }
                        } else {
                            error(CommonErrorMessage("File already exists!"), HttpStatusCode.Conflict)
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