package dk.sdu.cloud.file.http

import dk.sdu.cloud.file.api.DeliverMaterializedFileSystemAudit
import dk.sdu.cloud.file.api.DeliverMaterializedFileSystemResponse
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.VerifyFileKnowledgeResponse
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.IndexingService
import dk.sdu.cloud.file.util.tryWithFS
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import io.ktor.routing.Route

/**
 * The controller responsible for indexing related endpoints
 */
class IndexingController<Ctx : FSUserContext>(
    private val commandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val indexingService: IndexingService<Ctx>
) : Controller {
    override val baseContext: String = FileDescriptions.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(FileDescriptions.verifyFileKnowledge) { req ->
            tryWithFS(commandRunnerFactory, req.user) {
                ok(VerifyFileKnowledgeResponse(indexingService.verifyKnowledge(it, req.files)))
            }
        }

        implement(FileDescriptions.deliverMaterializedFileSystem) { req ->
            audit(DeliverMaterializedFileSystemAudit(req.rootsToMaterialized.keys.toList()))

            tryWithFS {
                val result = indexingService.runDiffOnRoots(req.rootsToMaterialized)
                ok(DeliverMaterializedFileSystemResponse(result.first))
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
