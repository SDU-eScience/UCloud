package dk.sdu.cloud.storage.http

import dk.sdu.cloud.file.api.DeliverMaterializedFileSystemAudit
import dk.sdu.cloud.file.api.DeliverMaterializedFileSystemResponse
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.VerifyFileKnowledgeResponse
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.services.FSCommandRunnerFactory
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.services.IndexingService
import dk.sdu.cloud.storage.util.tryWithFS
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
            logEntry(log, req)

            tryWithFS(commandRunnerFactory, req.user) {
                ok(VerifyFileKnowledgeResponse(indexingService.verifyKnowledge(it, req.files)))
            }
        }

        implement(FileDescriptions.deliverMaterializedFileSystem) { req ->
            logEntry(log, req)
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
