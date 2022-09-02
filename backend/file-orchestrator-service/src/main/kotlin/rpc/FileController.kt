package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.accounting.util.asController
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FilesControl
import dk.sdu.cloud.file.orchestrator.api.FilesStreamingSearchResult
import dk.sdu.cloud.file.orchestrator.service.FilesService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class FileController(private val files: FilesService) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        files.asController(Files).configure(rpcServer)

        implement(Files.copy) {
            ok(files.copy(actorAndProject, request))
        }

        implement(Files.createDownload) {
            ok(files.createDownload(actorAndProject, request))
        }

        implement(Files.createUpload) {
            ok(files.createUpload(actorAndProject, request))
        }

        implement(Files.createFolder) {
            ok(files.createFolder(actorAndProject, request))
        }

        implement(Files.move) {
            ok(files.move(actorAndProject, request))
        }

        implement(Files.trash) {
            ok(files.trash(actorAndProject, request))
        }

        implement(Files.emptyTrash) {
            ok(files.emptyTrash(actorAndProject, request))
        }

        implement(Files.updateAcl) {
            ok(files.updateAcl(actorAndProject, request))
        }

        implement(Files.streamingSearch) {
            files.streamingSearch(this)
            ok(FilesStreamingSearchResult.EndOfResults())
        }

        implement(FilesControl.addUpdate) {
            files.addTaskUpdate(actorAndProject, request)
            ok(Unit)
        }

        implement(FilesControl.markAsComplete) {
            files.markTaskAsComplete(actorAndProject, request)
            ok(Unit)
        }

        return@with
    }
}
