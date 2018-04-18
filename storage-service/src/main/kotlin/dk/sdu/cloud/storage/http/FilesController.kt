package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.services.FileSystemService
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.slf4j.LoggerFactory

class FilesController(private val fs: FileSystemService) {
    fun configure(routing: Route) = with(routing) {
        route("files") {
            implement(FileDescriptions.listAtPath) { request ->
                logEntry(log, request)
                if (!protect()) return@implement
                val principal = call.request.validatedPrincipal

                try {
                    ok(fs.ls(principal.subject, request.path))
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    error(CommonErrorMessage("Error"), HttpStatusCode.InternalServerError)
                }
            }

            implement(FileDescriptions.stat) { request ->
                logEntry(log, request)
                if (!protect()) return@implement
                val principal = call.request.validatedPrincipal

                try {
                    ok(fs.stat(principal.subject, request.path)!!)
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    error(CommonErrorMessage("Error"), HttpStatusCode.InternalServerError)
                }
            }

            implement(FileDescriptions.markAsFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement
                try {
                    fs.createFavorite(call.request.validatedPrincipal.subject, req.path)
                    ok(Unit)
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    error(CommonErrorMessage("Error"), HttpStatusCode.InternalServerError)
                }
            }

            implement(FileDescriptions.removeFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                try {
                    fs.removeFavorite(call.request.validatedPrincipal.subject, req.path)
                    ok(Unit)
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    error(CommonErrorMessage("Error"), HttpStatusCode.InternalServerError)
                }
            }

            implement(FileDescriptions.createDirectory) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                if (call.request.principalRole in setOf(Role.ADMIN, Role.SERVICE) && req.owner != null) {
                    log.debug("Authenticated as a privileged account. Using direct strategy")
                    try {
                        fs.mkdir(req.owner, req.path)
                        ok(Unit)
                    } catch (ex: Exception) {
                        log.warn(ex.stackTraceToString())
                        error(CommonErrorMessage("Error"), HttpStatusCode.InternalServerError)
                    }
                } else {
                    log.debug("Authenticated as a normal user. Using Jargon strategy")

                    try {
                        fs.mkdir(call.request.validatedPrincipal.subject, req.path)
                        ok(Unit)
                    } catch (ex: Exception) {
                        log.warn(ex.stackTraceToString())
                        error(CommonErrorMessage("Error"), HttpStatusCode.InternalServerError)
                    }
                }
            }

            implement(FileDescriptions.deleteFile) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                try {
                    fs.rmdir(call.request.validatedPrincipal.subject, req.path)
                    ok(Unit)
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    error(CommonErrorMessage("Error"), HttpStatusCode.InternalServerError)
                }
            }

            implement(FileDescriptions.move) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                try {
                    fs.move(call.request.validatedPrincipal.subject, req.path, req.newPath)
                    ok(Unit)
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    error(CommonErrorMessage("Error"), HttpStatusCode.InternalServerError)
                }
            }

            implement(FileDescriptions.copy) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                try {
                    fs.copy(call.request.validatedPrincipal.subject, req.path, req.newPath)
                    ok(Unit)
                } catch (ex: Exception) {
                    log.warn(ex.stackTraceToString())
                    error(CommonErrorMessage("Error"), HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FilesController::class.java)
    }
}