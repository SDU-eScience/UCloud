package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.currentUsername
import dk.sdu.cloud.auth.api.principalRole
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.util.CallResult
import dk.sdu.cloud.storage.util.tryWithFS
import dk.sdu.cloud.storage.util.tryWithFSAndTimeout
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import kotlinx.coroutines.experimental.io.writeStringUtf8
import org.slf4j.LoggerFactory

class FilesController<Ctx : FSUserContext>(
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val annotationService: FileAnnotationService<Ctx>,
    private val favoriteService: FavoriteService<Ctx>,
    private val fileLookupService: FileLookupService<Ctx>
) {
    fun configure(routing: Route) = with(routing) {
        route("files") {
            implement(FileDescriptions.listAtPath) { request ->
                logEntry(log, request)
                if (!protect()) return@implement

                tryWithFS(commandRunnerFactory, call.request.currentUsername) {
                    ok(
                        fileLookupService.listDirectory(
                            it,
                            request.path,
                            request.pagination,
                            request.sortBy ?: FileSortBy.TYPE,
                            request.order ?: SortOrder.ASCENDING
                        )
                    )
                }
            }

            implement(FileDescriptions.lookupFileInDirectory) { request ->
                logEntry(log, request)
                if (!protect()) return@implement

                tryWithFS(commandRunnerFactory, call.request.currentUsername) {
                    ok(
                        fileLookupService.lookupFileInDirectory(
                            it,
                            request.path,
                            request.itemsPerPage,
                            request.sortBy,
                            request.order
                        )
                    )
                }
            }

            implement(FileDescriptions.stat) { request ->
                logEntry(log, request)
                if (!protect()) return@implement

                tryWithFS(commandRunnerFactory, call.request.currentUsername) {
                    ok(fileLookupService.stat(it, request.path))
                }
            }

            implement(FileDescriptions.markAsFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                tryWithFSAndTimeout(commandRunnerFactory, call.request.currentUsername) {
                    favoriteService.markAsFavorite(it, req.path)
                    CallResult.Success(Unit, HttpStatusCode.NoContent)
                }
            }

            implement(FileDescriptions.removeFavorite) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                tryWithFSAndTimeout(commandRunnerFactory, call.request.currentUsername) {
                    favoriteService.removeFavorite(it, req.path)
                    CallResult.Success(Unit, HttpStatusCode.NoContent)
                }
            }

            implement(FileDescriptions.createDirectory) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                if (call.request.principalRole in setOf(Role.ADMIN, Role.SERVICE) && req.owner != null) {
                    log.debug("Authenticated as a privileged account. Using direct strategy")
                    tryWithFSAndTimeout(commandRunnerFactory, req.owner) {
                        coreFs.makeDirectory(it, req.path)
                        CallResult.Success(Unit, HttpStatusCode.NoContent)
                    }
                } else {
                    log.debug("Authenticated as a normal user. Using Jargon strategy")

                    tryWithFSAndTimeout(commandRunnerFactory, call.request.currentUsername) {
                        coreFs.makeDirectory(it, req.path)
                        CallResult.Success(Unit, HttpStatusCode.NoContent)
                    }
                }
            }

            implement(FileDescriptions.deleteFile) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                tryWithFSAndTimeout(commandRunnerFactory, call.request.currentUsername) {
                    coreFs.delete(it, req.path)
                    CallResult.Success(Unit, HttpStatusCode.NoContent)
                }
            }

            implement(FileDescriptions.move) { req ->
                logEntry(log, req)
                if (!protect()) return@implement
                tryWithFSAndTimeout(commandRunnerFactory, call.request.currentUsername) {
                    coreFs.move(it, req.path, req.newPath, req.policy ?: WriteConflictPolicy.OVERWRITE)
                    CallResult.Success(Unit, HttpStatusCode.NoContent)
                }
            }

            implement(FileDescriptions.copy) { req ->
                logEntry(log, req)
                if (!protect()) return@implement

                tryWithFSAndTimeout(commandRunnerFactory, call.request.currentUsername) {
                    coreFs.copy(it, req.path, req.newPath, req.policy ?: WriteConflictPolicy.OVERWRITE)
                    CallResult.Success(Unit, HttpStatusCode.NoContent)
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
                    FileAttribute.RAW_PATH
                )

                tryWithFS(commandRunnerFactory, call.request.currentUsername) {
                    call.respondDirectWrite(status = HttpStatusCode.OK, contentType = ContentType.Text.Plain) {
                        coreFs.tree(it, req.path, attributes).forEach {
                            writeStringUtf8(StringBuilder().apply {
                                appendToken(
                                    when (it.fileType) {
                                        FileType.FILE -> "F"
                                        FileType.DIRECTORY -> "D"
                                        else -> throw IllegalStateException()
                                    }
                                )

                                appendToken(it.inode)
                                appendToken(it.owner)
                                appendToken(it.timestamps.modified)

                                val hasChecksum = it.checksum.checksum.isNotEmpty()
                                appendToken(if (hasChecksum) '1' else '0')
                                if (hasChecksum) {
                                    appendToken(it.checksum.checksum)
                                    appendToken(it.checksum.algorithm)
                                }

                                // Must be last entry (path may contain commas)
                                append(it.rawPath)
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

                tryWithFS(commandRunnerFactory, req.proxyUser) {
                    annotationService.annotateFiles(it, req.path, req.annotatedWith)
                    ok(Unit)
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FilesController::class.java)
    }
}