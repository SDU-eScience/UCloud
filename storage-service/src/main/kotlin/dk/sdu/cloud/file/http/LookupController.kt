package dk.sdu.cloud.file.http

import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StorageFileAttribute
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString

class LookupController<Ctx : FSUserContext>(
    private val commandRunnerFactory: CommandRunnerFactoryForCalls<Ctx>,
    private val fileLookupService: FileLookupService<Ctx>,
    private val homeFolderService: HomeFolderService
) : Controller {
    private fun attributesOrDefault(attributes: String?): List<StorageFileAttribute> =
        attributes?.takeIf { it.isNotEmpty() }?.split(",")
            ?.mapNotNull { runCatching { StorageFileAttribute.valueOf(it) }.getOrNull() }
            ?: StorageFileAttribute.values().toList()

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(FileDescriptions.listAtPath) {
            audit(SingleFileAudit(request))

            commandRunnerFactory.withCtx(this) { ctx ->
                val attributes = attributesOrDefault(request.attributes)

                val result = fileLookupService.listDirectory(
                    ctx,
                    request.path,
                    if (request.itemsPerPage != -1) request.normalize() else null,
                    request.sortBy ?: FileSortBy.TYPE,
                    request.order ?: SortOrder.ASCENDING,
                    attributes,
                    request.type
                )

                ok(result)
            }
        }

        implement(FileDescriptions.lookupFileInDirectory) {
            audit(SingleFileAudit(request))

            commandRunnerFactory.withCtx(this) { ctx ->
                val attributes =
                    (attributesOrDefault(request.attributes) + listOf(StorageFileAttribute.path))

                val result = fileLookupService.lookupFileInDirectory(
                    ctx,
                    request.path,
                    request.itemsPerPage,
                    request.sortBy,
                    request.order,
                    attributes = attributes
                )

                ok(result)
            }
        }

        implement(FileDescriptions.stat) {
            audit(SingleFileAudit(request))

            commandRunnerFactory.withCtx(this) { ctx ->
                val attributes = attributesOrDefault(request.attributes)
                val result = fileLookupService.stat(
                    ctx,
                    request.path,
                    attributes = attributes
                )
                ok(result)
            }
        }

        implement(FileDescriptions.findHomeFolder) {
            try {
                val username = if (ctx.securityPrincipal.role in Roles.PRIVILEDGED && request.username.isNotBlank()) {
                    request.username
                } else {
                    ctx.securityPrincipal.username
                }

                ok(FindHomeFolderResponse(homeFolderService.findHomeFolder(username)))
            } catch (ex: Throwable) {
                log.warn(ex.stackTraceToString())
                throw ex
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
