package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.storage.services.CoreFileSystemService
import dk.sdu.cloud.storage.services.FSCommandRunnerFactory
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.services.FavoriteService
import dk.sdu.cloud.storage.services.FileAnnotationService
import dk.sdu.cloud.storage.services.FileAttribute
import dk.sdu.cloud.storage.services.FileLookupService
import dk.sdu.cloud.storage.util.CallResult
import dk.sdu.cloud.storage.util.tryWithFS
import dk.sdu.cloud.storage.util.tryWithFSAndTimeout
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import kotlinx.coroutines.io.writeStringUtf8
import org.slf4j.LoggerFactory

// TODO Split this into multiple controllers
class FilesController<Ctx : FSUserContext>(
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val annotationService: FileAnnotationService<Ctx>,
    private val favoriteService: FavoriteService<Ctx>,
    private val fileLookupService: FileLookupService<Ctx>
) : Controller {
    override val baseContext = FileDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileDescriptions.listAtPath) { request ->
            logEntry(log, request)
            audit(SingleFileAudit(null, request))

            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, request.path)
                val result = fileLookupService.listDirectory(
                    it,
                    request.path,
                    request.normalize(),
                    request.sortBy ?: FileSortBy.TYPE,
                    request.order ?: SortOrder.ASCENDING
                )

                audit(SingleFileAudit(stat.fileId, request))
                ok(result)
            }
        }

        implement(FileDescriptions.lookupFileInDirectory) { request ->
            logEntry(log, request)
            audit(SingleFileAudit(null, request))

            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                val result = fileLookupService.lookupFileInDirectory(
                    ctx,
                    request.path,
                    request.itemsPerPage,
                    request.sortBy,
                    request.order
                )

                val fileId = fileLookupService.stat(ctx, request.path).fileId
                audit(SingleFileAudit(fileId, request))
                ok(result)
            }
        }

        implement(FileDescriptions.stat) { request ->
            logEntry(log, request)
            audit(SingleFileAudit(null, request))

            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) {
                val result = fileLookupService.stat(it, request.path)
                audit(SingleFileAudit(result.fileId, request))
                ok(result)
            }
        }

        implement(FileDescriptions.markAsFavorite) { req ->
            logEntry(log, req)
            audit(SingleFileAudit(null, req))

            tryWithFSAndTimeout(commandRunnerFactory, call.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, req.path)

                if (!stat.favorited) {
                    favoriteService.markAsFavorite(it, req.path)

                    audit(SingleFileAudit(stat.fileId, req))
                    CallResult.Success(Unit, HttpStatusCode.OK)
                } else {
                    CallResult.Error(
                        CommonErrorMessage("Bad request. File is already a favorite"),
                        HttpStatusCode.BadRequest
                    )
                }
            }
        }

        implement(FileDescriptions.removeFavorite) { req ->
            logEntry(log, req)
            audit(SingleFileAudit(null, req))

            tryWithFSAndTimeout(commandRunnerFactory, call.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, req.path)
                if (stat.favorited) {
                    favoriteService.removeFavorite(it, req.path)

                    audit(SingleFileAudit(stat.fileId, req))
                    CallResult.Success(Unit, HttpStatusCode.OK)
                } else {
                    CallResult.Error(
                        CommonErrorMessage("Bad request. File is not a favorite"),
                        HttpStatusCode.BadRequest
                    )
                }
            }
        }

        implement(FileDescriptions.createDirectory) { req ->
            logEntry(log, req)

            if (call.securityPrincipal.role in Roles.PRIVILEDGED && req.owner != null) {
                log.debug("Authenticated as a privileged account. Using direct strategy")
                tryWithFSAndTimeout(commandRunnerFactory, req.owner) {
                    coreFs.makeDirectory(it, req.path)
                    CallResult.Success(Unit, HttpStatusCode.OK)
                }
            } else {
                log.debug("Authenticated as a normal user. Using Jargon strategy")

                tryWithFSAndTimeout(commandRunnerFactory, call.securityPrincipal.username) {
                    coreFs.makeDirectory(it, req.path)
                    CallResult.Success(Unit, HttpStatusCode.OK)
                }
            }
        }

        implement(FileDescriptions.deleteFile) { req ->
            logEntry(log, req)
            audit(SingleFileAudit(null, req))

            tryWithFSAndTimeout(commandRunnerFactory, call.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, req.path)
                coreFs.delete(it, req.path)

                audit(SingleFileAudit(stat.fileId, req))
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.move) { req ->
            logEntry(log, req)
            audit(SingleFileAudit(null, req))
            tryWithFSAndTimeout(commandRunnerFactory, call.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, req.path)
                coreFs.move(it, req.path, req.newPath, req.policy ?: WriteConflictPolicy.OVERWRITE)

                audit(SingleFileAudit(stat.fileId, req))
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.copy) { req ->
            logEntry(log, req)
            audit(SingleFileAudit(null, req))

            tryWithFSAndTimeout(commandRunnerFactory, call.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, req.path)
                coreFs.copy(it, req.path, req.newPath, req.policy ?: WriteConflictPolicy.OVERWRITE)
                audit(SingleFileAudit(stat.fileId, req))
                CallResult.Success(Unit, HttpStatusCode.OK)
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
            audit(SingleFileAudit(null, req))

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

            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                val inode = coreFs.stat(ctx, req.path, setOf(FileAttribute.INODE)).inode
                audit(SingleFileAudit(inode, req))
                okContentDeliveredExternally()

                call.respondDirectWrite(status = HttpStatusCode.OK, contentType = ContentType.Text.Plain) {
                    coreFs.tree(ctx, req.path, attributes).forEach {
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
            audit(SingleFileAudit(null, req))

            tryWithFS(commandRunnerFactory, req.proxyUser) {
                val stat = fileLookupService.stat(it, req.path)
                annotationService.annotateFiles(it, req.path, req.annotatedWith)
                audit(SingleFileAudit(stat.fileId, req))
                ok(Unit)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FilesController::class.java)
    }
}
