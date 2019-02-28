package dk.sdu.cloud.file.http

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.securityToken
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.BulkFileAudit
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.services.ACLService
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSACLEntity
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileRow
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode

class FileSecurityController<Ctx : FSUserContext>(
    private val commandRunnerFactory: CommandRunnerFactoryForCalls<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val aclService: ACLService<Ctx>,
    private val sensitivityService: FileSensitivityService<Ctx>,
    private val filePermissionsAcl: Set<String> = emptySet()
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(FileDescriptions.chmod) {
            val fileIdsUpdated = ArrayList<String>()
            audit(BulkFileAudit(fileIdsUpdated, request))
            requirePermissionToChangeFilePermissions()

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

        implement(FileDescriptions.updateAcl) {
            val fileIdsUpdated = ArrayList<String>()
            audit(BulkFileAudit(fileIdsUpdated, request))
            requirePermissionToChangeFilePermissions()

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

        implement(FileDescriptions.reclassify) {
            audit(SingleFileAudit(null, request))

            val user = ctx.securityPrincipal.username
            commandRunnerFactory.withCtx(this, user = user) { ctx ->
                val stat = coreFs.stat(ctx, request.path, setOf(FileAttribute.INODE))
                audit(SingleFileAudit(stat.inode, request))

                val sensitivity = request.sensitivity
                if (sensitivity != null) {
                    sensitivityService.setSensitivityLevel(ctx, request.path, sensitivity, user)
                } else {
                    sensitivityService.clearSensitivityLevel(ctx, request.path, user)
                }
            }
            ok(Unit)
        }
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

    private suspend fun CallHandler<*, *, *>.runCodeAsUnixOwner(path: String, handler: suspend (Ctx) -> Unit) {
        log.debug("We need to run request at '$path' as real owner")
        lateinit var stat: FileRow
        commandRunnerFactory.withCtx(this, SERVICE_USER) { ctx ->
            stat = coreFs.stat(ctx, path, setOf(FileAttribute.OWNER, FileAttribute.XOWNER))
        }

        val realOwner = stat.xowner
        val creator = stat.owner

        val user = ctx.securityPrincipal.username
        log.debug("Real owner is $realOwner and user is $user")

        if (realOwner != user) throw FSException.PermissionException()

        commandRunnerFactory.withCtx(this, creator) { ctx -> handler(ctx) }
    }


    companion object : Loggable {
        override val log = logger()
    }
}
