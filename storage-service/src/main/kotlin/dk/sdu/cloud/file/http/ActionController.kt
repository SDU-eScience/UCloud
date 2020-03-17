package dk.sdu.cloud.file.http

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.api.sensitivityLevel
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.util.CallResult
import dk.sdu.cloud.service.Controller
import io.ktor.http.HttpStatusCode

class ActionController<Ctx : FSUserContext>(
    private val commandRunnerFactory: CommandRunnerFactoryForCalls<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val sensitivityService: FileSensitivityService<Ctx>,
    private val fileLookupService: FileLookupService<Ctx>
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(FileDescriptions.createDirectory) {
            val sensitivity = request.sensitivity
            if (ctx.securityPrincipal.role in Roles.PRIVILEDGED && request.owner != null) {
                val owner = request.owner!!
                commandRunnerFactory.withCtxAndTimeout(this, user = owner) {
                    coreFs.makeDirectory(it, request.path)
                    if (sensitivity != null) {
                        sensitivityService.setSensitivityLevel(it, request.path, sensitivity)
                    }
                    CallResult.Success(Unit, HttpStatusCode.OK)
                }
            } else {
                commandRunnerFactory.withCtxAndTimeout(this) {
                    coreFs.makeDirectory(it, request.path)
                    if (sensitivity != null) {
                        sensitivityService.setSensitivityLevel(
                            it,
                            request.path,
                            sensitivity
                        )
                    }
                    CallResult.Success(Unit, HttpStatusCode.OK)
                }
            }
        }

        implement(FileDescriptions.deleteFile) {
            audit(SingleFileAudit(request))

            commandRunnerFactory.withCtxAndTimeout(this) {
                coreFs.delete(it, request.path)
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.move) {
            audit(SingleFileAudit(request))
            commandRunnerFactory.withCtxAndTimeout(this) {
                val stat = fileLookupService.stat(it, request.path)
                val targetPath = coreFs.move(
                    it,
                    request.path,
                    request.newPath,
                    request.policy ?: WriteConflictPolicy.OVERWRITE
                )

                // Don't change from inherit if we can avoid it.
                // We don't need to change sensitivity of children (if they inherit they will inherit from us).
                val newSensitivity = fileLookupService.stat(it, targetPath).sensitivityLevel
                if (stat.sensitivityLevel != newSensitivity) {
                    sensitivityService.setSensitivityLevel(
                        it,
                        targetPath,
                        stat.sensitivityLevel
                    )
                }

                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }

        implement(FileDescriptions.copy) {
            audit(SingleFileAudit(request))
            commandRunnerFactory.withCtxAndTimeout(this) {
                val stat = fileLookupService.stat(it, request.path)
                val pathNormalized = request.path.normalize()
                val targetPathNormalized = request.newPath.normalize()
                if (stat.fileType == FileType.DIRECTORY && targetPathNormalized.startsWith(pathNormalized)) {
                    //edge case check
                    // /home/path/dir -> /home/path/dirA
                    // Starts with same but are different dirs.
                    val target = "$targetPathNormalized/"
                    if (target.indexOf("$pathNormalized/") == 0 && request.policy != WriteConflictPolicy.RENAME) {
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }
                }
                coreFs.copy(
                    it,
                    request.path,
                    request.newPath,
                    stat.sensitivityLevel,
                    request.policy ?: WriteConflictPolicy.OVERWRITE
                )
                CallResult.Success(Unit, HttpStatusCode.OK)
            }
        }
    }
}
