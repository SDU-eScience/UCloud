package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.PRIVILEGED_ROLES
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.logEntry
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.VerifyFileKnowledgeResponse
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
            if (!protect(PRIVILEGED_ROLES)) return@implement

            tryWithFS(commandRunnerFactory, req.user) {
                ok(VerifyFileKnowledgeResponse(
                    indexingService.verifyKnowledge(it, req.files)
                ))
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}