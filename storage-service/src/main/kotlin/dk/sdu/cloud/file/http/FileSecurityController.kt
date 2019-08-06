package dk.sdu.cloud.file.http

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.file.api.BulkFileAudit
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.services.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class FileSecurityController<Ctx : FSUserContext>(
    private val commandRunnerFactory: CommandRunnerFactoryForCalls<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val aclWorker: ACLWorker,
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

            aclWorker.updateAcl(request, ctx.securityPrincipal.username)
            ok(Unit)
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

    companion object : Loggable {
        override val log = logger()
    }
}
