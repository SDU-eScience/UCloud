package dk.sdu.cloud.file.http

import dk.sdu.cloud.FindByStringId
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
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileAttribute
import dk.sdu.cloud.file.services.FileRow
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
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
            ok(Unit)
        }

        implement(FileDescriptions.updateAcl) {
            // We cannot supply a list of file IDs since this call is async
            audit(BulkFileAudit(emptyList(), request))
            requirePermissionToChangeFilePermissions()
            val (_, owner) = checkPermissionsAndReturnOwners(request.path)

            ok(FindByStringId(aclService.updateAcl(request, owner, ctx.securityPrincipal.username)))
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

    private data class CreatorAndOwner(val creator: String, val owner: String)

    private suspend fun CallHandler<*, *, *>.checkPermissionsAndReturnOwners(path: String): CreatorAndOwner {
        log.debug("We need to run request at '$path' as real creator")
        lateinit var stat: FileRow
        commandRunnerFactory.withCtx(this, SERVICE_USER) { ctx ->
            stat = coreFs.stat(ctx, path, setOf(FileAttribute.CREATOR, FileAttribute.OWNER))
        }

        val realOwner = stat.owner
        val creator = stat.creator

        val user = ctx.securityPrincipal.username
        log.debug("Real creator is $realOwner and user is $user")

        if (realOwner != user) throw FSException.PermissionException()

        return CreatorAndOwner(creator, realOwner)
    }

    private suspend fun CallHandler<*, *, *>.runCodeAsUnixOwner(path: String, handler: suspend (Ctx) -> Unit) {
        val (creator, _) = checkPermissionsAndReturnOwners(path)
        commandRunnerFactory.withCtx(this, creator) { ctx -> handler(ctx) }
    }


    companion object : Loggable {
        override val log = logger()
    }
}
