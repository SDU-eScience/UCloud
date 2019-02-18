package dk.sdu.cloud.file.http

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.securityToken
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.BulkFileAudit
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.services.ACLService
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSACLEntity
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileAnnotationService
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.file.util.CallResult
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.tryWithFS
import dk.sdu.cloud.file.util.tryWithFSAndTimeout
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import kotlinx.coroutines.io.writeStringUtf8
import org.slf4j.LoggerFactory

// TODO Split this into multiple controllers
class FilesController<Ctx : FSUserContext>(
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val annotationService: FileAnnotationService<Ctx>,
    private val fileLookupService: FileLookupService<Ctx>,
    private val sensitivityService: FileSensitivityService<Ctx>,
    private val aclService: ACLService<Ctx>,
    private val homeFolderService: HomeFolderService,
    private val filePermissionsAcl: Set<String> = emptySet()
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileDescriptions.listAtPath) {
            audit(SingleFileAudit(null, request))

            tryWithFS(commandRunnerFactory, ctx.securityPrincipal.username) {
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

        implement(FileDescriptions.lookupFileInDirectory) {
            audit(SingleFileAudit(null, request))

            tryWithFS(commandRunnerFactory, ctx.securityPrincipal.username) { ctx ->
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

        implement(FileDescriptions.stat) {
            audit(SingleFileAudit(null, request))

            tryWithFS(commandRunnerFactory, ctx.securityPrincipal.username) {
                val result = fileLookupService.stat(it, request.path)
                audit(SingleFileAudit(result.fileId, request))
                ok(result)
            }
        }

        implement(FileDescriptions.createDirectory) {
            if (ctx.securityPrincipal.role in Roles.PRIVILEDGED && request.owner != null) {
                val owner = request.owner!!
                tryWithFSAndTimeout(commandRunnerFactory, owner) {
                    coreFs.makeDirectory(it, request.path)
                    sensitivityService.setSensitivityLevel(it, request.path, request.sensitivity, null)
                    CallResult.Success(Unit, HttpStatusCode.OK)
                }
            } else {
                tryWithFSAndTimeout(commandRunnerFactory, ctx.securityPrincipal.username) {
                    coreFs.makeDirectory(it, request.path)
                    sensitivityService.setSensitivityLevel(
                        it,
                        request.path,
                        request.sensitivity,
                        ctx.securityPrincipal.username
                    )
                    CallResult.Success(Unit, HttpStatusCode.OK)
                }
            }
        }

        implement(FileDescriptions.deleteFile) {
            audit(SingleFileAudit(null, request))

            tryWithFSAndTimeout(commandRunnerFactory, ctx.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, request.path)
                coreFs.delete(it, request.path)

                audit(SingleFileAudit(stat.fileId, request))
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.move) {
            audit(SingleFileAudit(null, request))
            tryWithFSAndTimeout(commandRunnerFactory, ctx.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, request.path)
                coreFs.move(it, request.path, request.newPath, request.policy ?: WriteConflictPolicy.OVERWRITE)

                audit(SingleFileAudit(stat.fileId, request))
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.copy) {
            audit(SingleFileAudit(null, request))

            tryWithFSAndTimeout(commandRunnerFactory, ctx.securityPrincipal.username) {
                val stat = fileLookupService.stat(it, request.path)
                coreFs.copy(it, request.path, request.newPath, request.policy ?: WriteConflictPolicy.OVERWRITE)
                audit(SingleFileAudit(stat.fileId, request))
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.syncFileList) {
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

            audit(SingleFileAudit(null, request))

            fun StringBuilder.appendToken(token: Any?) {
                append(token.toString())
                append(',')
            }

            val attributes = setOf(
                FileAttribute.FILE_TYPE,
                FileAttribute.UNIX_MODE,
                FileAttribute.OWNER,
                FileAttribute.XOWNER,
                FileAttribute.GROUP,
                FileAttribute.SIZE,
                FileAttribute.TIMESTAMPS,
                FileAttribute.INODE,
                FileAttribute.CHECKSUM,
                FileAttribute.RAW_PATH
            )

            with(ctx as HttpCall) {
                tryWithFS(commandRunnerFactory, ctx.securityPrincipal.username) { ctx ->
                    val inode = coreFs.stat(ctx, request.path, setOf(FileAttribute.INODE)).inode
                    audit(SingleFileAudit(inode, request))
                    okContentAlreadyDelivered()

                    call.respond(
                        DirectWriteContent(contentType = ContentType.Text.Plain, status = HttpStatusCode.OK) {
                            coreFs.tree(ctx, request.path, attributes).forEach {
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
                    )
                }
            }
        }

        implement(FileDescriptions.annotate) {
            audit(SingleFileAudit(null, request))

            tryWithFS(commandRunnerFactory, request.proxyUser) {
                val stat = fileLookupService.stat(it, request.path)
                annotationService.annotateFiles(it, request.path, request.annotatedWith)
                audit(SingleFileAudit(stat.fileId, request))
                ok(Unit)
            }
        }

        implement(FileDescriptions.createLink) {
            audit(SingleFileAudit(null, request))

            tryWithFS(commandRunnerFactory, ctx.securityPrincipal.username) { ctx ->
                val created = coreFs.createSymbolicLink(ctx, request.linkTargetPath, request.linkPath)
                audit(SingleFileAudit(coreFs.stat(ctx, request.linkPath, setOf(FileAttribute.INODE)).inode, request))

                ok(fileLookupService.stat(ctx, created.path))
            }
        }

        implement(FileDescriptions.chmod) {
            val fileIdsUpdated = ArrayList<String>()
            audit(BulkFileAudit(fileIdsUpdated, request))
            requirePermissionToChangeFilePermissions()

            tryWithFS {
                runCodeAsUnixOwner(request.path) { ctx ->
                    coreFs.chmod(
                        ctx,
                        request.path,
                        request.owner,
                        request.group,
                        request.other,
                        request.recurse,
                        fileIdsUpdated
                    )
                }
                ok(Unit)
            }
        }

        implement(FileDescriptions.updateAcl) {
            val fileIdsUpdated = ArrayList<String>()
            audit(BulkFileAudit(fileIdsUpdated, request))
            requirePermissionToChangeFilePermissions()

            tryWithFS {
                runCodeAsUnixOwner(request.path) { ctx ->
                    request.changes.forEach { change ->
                        val entity =
                            if (change.isUser) FSACLEntity.User(change.entity) else FSACLEntity.Group(change.entity)

                        if (change.revoke) {
                            log.debug("revoking")
                            aclService.revokeRights(ctx, request.path, entity, request.recurse)
                        } else {
                            log.debug("granting")
                            aclService.grantRights(ctx, request.path, entity, change.rights, request.recurse)
                        }
                    }

                    try {
                        if (request.recurse) {
                            coreFs.tree(ctx, request.path, setOf(FileAttribute.INODE)).forEach {
                                fileIdsUpdated.add(it.inode)
                            }
                        } else {
                            val fileId = coreFs.stat(ctx, request.path, setOf(FileAttribute.INODE)).inode
                            fileIdsUpdated.add(fileId)
                        }
                    } catch (ex: Exception) {
                        log.info(ex.stackTraceToString())
                    }

                    ok(Unit)
                }
            }
        }

        implement(FileDescriptions.reclassify) {
            audit(SingleFileAudit(null, request))

            val user = ctx.securityPrincipal.username
            tryWithFS(commandRunnerFactory, user) { ctx ->
                val stat = coreFs.stat(ctx, request.path, setOf(FileAttribute.INODE))
                audit(SingleFileAudit(stat.inode, request))

                sensitivityService.setSensitivityLevel(ctx, request.path, request.sensitivity, user)
            }
            ok(Unit)
        }

        implement(FileDescriptions.findHomeFolder) {
            ok(FindHomeFolderResponse(homeFolderService.findHomeFolder(request.username)))
        }
    }

    private suspend fun CallHandler<*, *, *>.runCodeAsUnixOwner(path: String, handler: suspend (Ctx) -> Unit) {
        log.debug("We need to run request at '$path' as real owner")
        val stat = commandRunnerFactory.withContext(SERVICE_USER) { ctx ->
            coreFs.stat(ctx, path, setOf(FileAttribute.OWNER, FileAttribute.XOWNER))
        }

        val realOwner = stat.xowner
        val creator = stat.owner

        val user = ctx.securityPrincipal.username
        log.debug("Real owner is $realOwner and user is $user")

        if (realOwner != user) throw FSException.PermissionException()

        commandRunnerFactory.withContext(creator) { ctx -> handler(ctx) }
    }

    private fun CallHandler<*, *, *>.requirePermissionToChangeFilePermissions() {
        val securityToken = ctx.securityToken
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
