package dk.sdu.cloud.file.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileSortBy
import dk.sdu.cloud.file.api.FindHomeFolderResponse
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.api.SortOrder
import dk.sdu.cloud.file.api.StorageFileAttribute
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.service.Controller

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
            audit(SingleFileAudit(null, request))

            commandRunnerFactory.withCtx(this) { ctx ->
                val stat = fileLookupService.stat(ctx, request.path, listOf(StorageFileAttribute.fileId))

                val attributes = attributesOrDefault(request.attributes)

                val result = fileLookupService.listDirectory(
                    ctx,
                    request.path,
                    request.normalize(),
                    request.sortBy ?: FileSortBy.TYPE,
                    request.order ?: SortOrder.ASCENDING,
                    attributes
                )

                audit(SingleFileAudit(stat.fileId, request))
                ok(result)
            }
        }

        implement(FileDescriptions.lookupFileInDirectory) {
            audit(SingleFileAudit(null, request))

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

                val fileId = fileLookupService.stat(ctx, request.path).fileId
                audit(SingleFileAudit(fileId, request))
                ok(result)
            }
        }

        implement(FileDescriptions.stat) {
            audit(SingleFileAudit(null, request))

            commandRunnerFactory.withCtx(this) { ctx ->
                val attributes = attributesOrDefault(request.attributes) +
                        setOf(StorageFileAttribute.fileId) // needed for audit
                val result = fileLookupService.stat(
                    ctx,
                    request.path,
                    attributes = attributes
                )
                audit(SingleFileAudit(result.fileId, request))
                ok(result)
            }
        }

        implement(FileDescriptions.findHomeFolder) {
            ok(FindHomeFolderResponse(homeFolderService.findHomeFolder(request.username)))
        }
    }
}
