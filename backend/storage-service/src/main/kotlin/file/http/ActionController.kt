package dk.sdu.cloud.file.http

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.LimitChecker
import dk.sdu.cloud.file.util.CallResult
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.toActor
import io.ktor.http.HttpStatusCode

class ActionController<Ctx : FSUserContext>(
    private val commandRunnerFactory: CommandRunnerFactoryForCalls<Ctx>,
    private val coreFs: CoreFileSystemService<Ctx>,
    private val fileLookupService: FileLookupService<Ctx>,
    private val limitChecker: LimitChecker<Ctx>
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(FileDescriptions.createPersonalRepository) {
            commandRunnerFactory.withCtx(this, SERVICE_USER) {
                coreFs.makeDirectory(it, "/projects/${request.project}/${PERSONAL_REPOSITORY}/${request.username}")
            }
            ok(Unit)
        }

        implement(FileDescriptions.createDirectory) {
            val sensitivity = request.sensitivity
            if (ctx.securityPrincipal.role in Roles.PRIVILEGED && request.owner != null) {
                val owner = request.owner!!
                commandRunnerFactory.withCtxAndTimeout(this, user = owner) {
                    coreFs.makeDirectory(it, request.path)
                    if (sensitivity != null) {
                        coreFs.setSensitivityLevel(it, request.path, sensitivity)
                    }
                    CallResult.Success(Unit, HttpStatusCode.OK)
                }
            } else {
                commandRunnerFactory.withCtxAndTimeout(this) {
                    coreFs.makeDirectory(it, request.path)
                    if (sensitivity != null) {
                        coreFs.setSensitivityLevel(
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
                    coreFs.setSensitivityLevel(
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

        implement(FileDescriptions.normalizePermissions) {
            commandRunnerFactory.withCtx(this) { ctx ->
                coreFs.normalizePermissions(ctx, request.path)
            }

            ok(Unit)
        }

        implement(FileDescriptions.updateQuota) {
            limitChecker.setQuota(
                ctx.securityPrincipal.toActor(),
                request.path,
                request.quotaInBytes,
                request.additive
            )
            ok(Unit)
        }

        implement(FileDescriptions.retrieveQuota) {
            val quota = limitChecker.retrieveQuota(ctx.securityPrincipal.toActor(), request.path)
            var usage: Long? = null
            if (request.includeUsage) {
                commandRunnerFactory.withCtx(this, SERVICE_USER) {
                    // Use service user since we have already passed permission check for reading quota
                    usage = coreFs.estimateRecursiveStorageUsedMakeItFast(it, request.path)
                }
            }

            ok(quota.copy(quotaUsed = usage))
        }

        implement(FileDescriptions.transferQuota) {
            limitChecker.transferQuota(
                ctx.securityPrincipal.toActor(),
                projectHomeDirectory(ctx.project ?: throw RPCException("Missing project", HttpStatusCode.BadRequest)),
                request.path,
                request.quotaInBytes
            )
            ok(Unit)
        }
    }
}
