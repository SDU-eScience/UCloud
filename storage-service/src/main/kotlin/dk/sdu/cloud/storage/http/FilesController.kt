package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.services.FileSystemException
import dk.sdu.cloud.storage.services.FileSystemService
import dk.sdu.cloud.storage.services.tryWithFS
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.io.writeStringUtf8
import org.slf4j.LoggerFactory

class FilesController(private val fs: FileSystemService) {
    fun configure(routing: Route) = with(routing) {
        route("files") {
            implement(FileDescriptions.listAtPath) { request ->
                logEntry(log, request)
                if (!protect()) return@implement
                val principal = call.request.validatedPrincipal

                tryWithFS {
                    ok(fs.ls(principal.subject, request.path))
                }
            }

            implement(FileDescriptions.stat) { request ->
                logEntry(log, request)
                if (!protect()) return@implement
                val principal = call.request.validatedPrincipal

                tryWithFS {
                    ok(
                        fs.stat(principal.subject, request.path) ?: throw FileSystemException.NotFound(request.path)
                    )
                }
            }

            implement(FileDescriptions.markAsFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                tryWithFS {
                    fs.createFavorite(call.request.validatedPrincipal.subject, req.path)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.removeFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                tryWithFS {
                    fs.removeFavorite(call.request.validatedPrincipal.subject, req.path)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.createDirectory) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                if (call.request.principalRole in setOf(Role.ADMIN, Role.SERVICE) && req.owner != null) {
                    log.debug("Authenticated as a privileged account. Using direct strategy")
                    tryWithFS {
                        fs.mkdir(req.owner, req.path)
                        ok(Unit)
                    }
                } else {
                    log.debug("Authenticated as a normal user. Using Jargon strategy")

                    tryWithFS {
                        fs.mkdir(call.request.validatedPrincipal.subject, req.path)
                        ok(Unit)
                    }
                }
            }

            implement(FileDescriptions.deleteFile) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                tryWithFS {
                    fs.rmdir(call.request.validatedPrincipal.subject, req.path)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.move) { req ->
                logEntry(log, req)
                if (!protect()) return@implement
                tryWithFS {
                    fs.move(call.request.validatedPrincipal.subject, req.path, req.newPath)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.copy) { req ->
                logEntry(log, req)
                if (!protect()) return@implement
                tryWithFS {
                    fs.copy(call.request.validatedPrincipal.subject, req.path, req.newPath)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.syncFileList) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                fun StringBuilder.appendToken(token: Any?) {
                    append(token.toString())
                    append(',')
                }

                tryWithFS {
                    call.respondDirectWrite(status = HttpStatusCode.OK, contentType = ContentType.Text.Plain) {
                        fs.syncList(call.request.validatedPrincipal.subject, req.path, req.modifiedSince ?: 0) {
                            writeStringUtf8(StringBuilder().apply {
                                appendToken(
                                    when (it.type) {
                                        FileType.FILE -> "F"
                                        FileType.DIRECTORY -> "D"
                                        else -> throw IllegalStateException()
                                    }
                                )

                                appendToken(it.user)
                                appendToken(it.modifiedAt)

                                val hasChecksum = it.checksum != null && it.checksumType != null
                                appendToken(if (hasChecksum) '1' else '0')
                                if (hasChecksum) {
                                    appendToken(it.checksum)
                                    appendToken(it.checksumType)
                                }

                                // Must be last entry (path may contain commas)
                                append(it.path)
                                append('\n')

                                toString()
                            })
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FilesController::class.java)
    }
}