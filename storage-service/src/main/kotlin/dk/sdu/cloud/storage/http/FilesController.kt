package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.services.FSException
import dk.sdu.cloud.storage.services.FileSystemService
import dk.sdu.cloud.storage.services.cephfs.FileAttribute
import dk.sdu.cloud.storage.services.tryWithFS
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import kotlinx.coroutines.experimental.io.writeStringUtf8
import org.slf4j.LoggerFactory

class FilesController(private val fs: FileSystemService) {
    fun configure(routing: Route) = with(routing) {
        route("files") {
            implement(FileDescriptions.listAtPath) { request ->
                logEntry(log, request)
                if (!protect()) return@implement

                tryWithFS(fs, call.request.currentUsername) {
                    ok(fs.ls(it, request.path))
                }
            }

            implement(FileDescriptions.stat) { request ->
                logEntry(log, request)
                if (!protect()) return@implement

                tryWithFS(fs, call.request.currentUsername) {
                    ok(fs.stat(it, request.path) ?: throw FSException.NotFound(request.path))
                }
            }

            implement(FileDescriptions.markAsFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                tryWithFS(fs, call.request.currentUsername) {
                    fs.createFavorite(it, req.path)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.removeFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                tryWithFS(fs, call.request.currentUsername) {
                    fs.removeFavorite(it, req.path)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.createDirectory) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                if (call.request.principalRole in setOf(Role.ADMIN, Role.SERVICE) && req.owner != null) {
                    log.debug("Authenticated as a privileged account. Using direct strategy")
                    tryWithFS(fs, req.owner) {
                        fs.mkdir(it, req.path)
                        ok(Unit)
                    }
                } else {
                    log.debug("Authenticated as a normal user. Using Jargon strategy")

                    tryWithFS(fs, call.request.currentUsername) {
                        fs.mkdir(it, req.path)
                        ok(Unit)
                    }
                }
            }

            implement(FileDescriptions.deleteFile) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                tryWithFS(fs, call.request.currentUsername) {
                    fs.rmdir(it, req.path)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.move) { req ->
                logEntry(log, req)
                if (!protect()) return@implement
                tryWithFS(fs, call.request.currentUsername) {
                    fs.move(it, req.path, req.newPath)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.copy) { req ->
                logEntry(log, req)
                if (!protect()) return@implement
                tryWithFS(fs, call.request.currentUsername) {
                    fs.copy(it, req.path, req.newPath)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.syncFileList) { req ->
                // Note(Dan): This is not serialized as JSON for a reason.
                //
                // We really want to not do pagination here. There is no real reason for it (we want the full list in
                // the sync client).
                //
                // We have also seen problems with the JSON libraries used when we start generating
                // large JSON payloads. For example the "ls" endpoint has had problems when listing 1K files.
                //
                // It is also not unlikely, although I have not checked, that the JSON library would keep the entire
                // JSON structure in memory until it is shipped. These typically take up significantly more memory
                // than the JSON payload itself.
                //
                // This implementation does nothing to prevent GC when needed. Once a file entry is sent over the wire
                // it should be eligible for GC.

                logEntry(log, req)
                if (!protect()) return@implement

                fun StringBuilder.appendToken(token: Any?) {
                    append(token.toString())
                    append(',')
                }

                val attributes = setOf(
                    FileAttribute.FILE_TYPE,
                    FileAttribute.UNIX_MODE,
                    FileAttribute.OWNER,
                    FileAttribute.GROUP,
                    FileAttribute.SIZE,
                    FileAttribute.TIMESTAMPS,
                    FileAttribute.INODE,
                    FileAttribute.CHECKSUM,
                    FileAttribute.PATH
                )

                tryWithFS(fs, call.request.currentUsername) {
                    call.respondDirectWrite(status = HttpStatusCode.OK, contentType = ContentType.Text.Plain) {
                        fs.tree(it, req.path, attributes) {
                            writeStringUtf8(StringBuilder().apply {
                                appendToken(
                                    when (it.fileType) {
                                        FileType.FILE -> "F"
                                        FileType.DIRECTORY -> "D"
                                        else -> throw IllegalStateException()
                                    }
                                )

                                appendToken(it.inode!!)
                                appendToken(it.owner!!)
                                appendToken(it.timestamps!!.modified)

                                val hasChecksum = it.checksum != null
                                appendToken(if (hasChecksum) '1' else '0')
                                if (it.checksum != null) {
                                    appendToken(it.checksum.checksum)
                                    appendToken(it.checksum.type)
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

            implement(FileDescriptions.annotate) { req ->
                logEntry(log, req)
                if (!protect(listOf(Role.ADMIN, Role.SERVICE))) return@implement

                tryWithFS(fs, req.proxyUser) {
                    fs.annotateFiles(it, req.path, req.annotatedWith)
                    ok(Unit)
                }
            }

            implement(FileDescriptions.markAsOpenAccess) { req ->
                logEntry(log, req)
                if (!protect(listOf(Role.ADMIN, Role.SERVICE))) return@implement

                tryWithFS(fs, req.proxyUser) {
                    fs.markAsOpenAccess(it, req.path)
                    ok(Unit)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FilesController::class.java)
    }
}