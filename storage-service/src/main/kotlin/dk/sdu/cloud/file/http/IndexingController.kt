package dk.sdu.cloud.file.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.file.api.DeliverMaterializedFileSystemAudit
import dk.sdu.cloud.file.api.DeliverMaterializedFileSystemResponse
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.KnowledgeMode
import dk.sdu.cloud.file.api.VerifyFileKnowledgeResponse
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.IndexingService
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable

/**
 * The controller responsible for indexing related endpoints
 */
class IndexingController<Ctx : FSUserContext>(
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val indexingService: IndexingService<Ctx>
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileDescriptions.verifyFileKnowledge) {
            commandRunnerFactory.withContext(request.user) { ctx ->
                val mode = request.mode ?: KnowledgeMode.List()
                ok(VerifyFileKnowledgeResponse(indexingService.verifyKnowledge(ctx, request.files, mode)))
            }
        }

        implement(FileDescriptions.deliverMaterializedFileSystem) {
            audit(DeliverMaterializedFileSystemAudit(request.rootsToMaterialized.keys.toList()))

            val result = indexingService.runDiffOnRoots(request.rootsToMaterialized)
            ok(DeliverMaterializedFileSystemResponse(result.first))
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
