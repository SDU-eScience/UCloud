package dk.sdu.cloud.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.file.api.BulkFileAudit
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.service.securityToken
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.services.ACLService
import dk.sdu.cloud.storage.services.CoreFileSystemService
import dk.sdu.cloud.storage.services.FSACLEntity
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
    private val fileLookupService: FileLookupService<Ctx>,
    private val aclService: ACLService<Ctx>,
    private val filePermissionsAcl: Set<String> = emptySet()
) : Controller {
    override val baseContext = FileDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileDescriptions.listAtPath) { request ->
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
            audit(SingleFileAudit(null, request))

            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) {
                val result = fileLookupService.stat(it, request.path)
                audit(SingleFileAudit(result.fileId, request))
                ok(result)
            }
        }

        implement(FileDescriptions.markAsFavorite) { req ->
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
            audit(SingleFileAudit(null, req))

            tryWithFSAndTimeout(commandRunnerFactory, call.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, req.path)
                coreFs.delete(it, req.path)

                audit(SingleFileAudit(stat.fileId, req))
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.move) { req ->
            audit(SingleFileAudit(null, req))
            tryWithFSAndTimeout(commandRunnerFactory, call.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, req.path)
                coreFs.move(it, req.path, req.newPath, req.policy ?: WriteConflictPolicy.OVERWRITE)

                audit(SingleFileAudit(stat.fileId, req))
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.copy) { req ->
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
            audit(SingleFileAudit(null, req))

            tryWithFS(commandRunnerFactory, req.proxyUser) {
                val stat = fileLookupService.stat(it, req.path)
                annotationService.annotateFiles(it, req.path, req.annotatedWith)
                audit(SingleFileAudit(stat.fileId, req))
                ok(Unit)
            }
        }

        implement(FileDescriptions.createLink) { req ->
            audit(SingleFileAudit(null, req))

            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                coreFs.createSymbolicLink(ctx, req.linkTargetPath, req.linkPath)
                audit(SingleFileAudit(coreFs.stat(ctx, req.linkPath, setOf(FileAttribute.INODE)).inode, req))
            }

            ok(Unit)
        }

        implement(FileDescriptions.chmod) { req ->
            val fileIdsUpdated = ArrayList<String>()
            audit(BulkFileAudit(fileIdsUpdated, req))
            requirePermissionToChangeFilePermissions()

            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                coreFs.chmod(ctx, req.path, req.owner, req.group, req.other, req.recurse, fileIdsUpdated)
                ok(Unit)
            }
        }

        implement(FileDescriptions.updateAcl) { req ->
            val fileIdsUpdated = ArrayList<String>()
            audit(BulkFileAudit(fileIdsUpdated, req))
            requirePermissionToChangeFilePermissions()

            tryWithFS(commandRunnerFactory, call.securityPrincipal.username) { ctx ->
                req.changes.forEach { change ->
                    val entity =
                        if (change.isUser) FSACLEntity.User(change.entity) else FSACLEntity.Group(change.entity)

                    if (change.revoke) {
                        log.debug("revoking")
                        aclService.revokeRights(ctx, req.path, entity, req.recurse)
                    } else {
                        log.debug("granting")
                        aclService.grantRights(ctx, req.path, entity, change.rights, req.recurse)
                    }
                }

                try {
                    if (req.recurse) {
                        coreFs.tree(ctx, req.path, setOf(FileAttribute.INODE)).forEach {
                            fileIdsUpdated.add(it.inode)
                        }
                    } else {
                        val fileId = coreFs.stat(ctx, req.path, setOf(FileAttribute.INODE)).inode
                        fileIdsUpdated.add(fileId)
                    }
                } catch (ex: Exception) {
                    log.info(ex.stackTraceToString())
                }

                ok(Unit)
            }
        }
    }

    private fun RESTHandler<*, *, *, *>.requirePermissionToChangeFilePermissions() {
        val securityToken = call.securityToken
        if (
            securityToken.principal.username !in filePermissionsAcl &&
            securityToken.extendedBy !in filePermissionsAcl
        ) {
            log.debug("Token was extended by ${securityToken.extendedBy} but is not in $filePermissionsAcl")
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FilesController::class.java)
    }
}
