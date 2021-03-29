package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.service.FilesService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class FileController(private val files: FilesService) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Files.browse) {
            ok(files.browse(actorAndProject.actor, request))
        }

        implement(Files.copy) {
            ok(files.copy(actorAndProject.actor, request))
        }

        implement(Files.createDownload) {
            ok(files.createDownload(actorAndProject.actor, request))
        }

        implement(Files.createUpload) {
            ok(files.createUpload(actorAndProject.actor, request))
        }

        implement(Files.createFolder) {
            ok(files.createFolder(actorAndProject.actor, request))
        }

        implement(Files.delete) {
            ok(files.delete(actorAndProject.actor, request))
        }

        implement(Files.move) {
            ok(files.move(actorAndProject.actor, request))
        }

        implement(Files.retrieve) {
            ok(files.retrieve(actorAndProject.actor, request))
        }

        implement(Files.trash) {
            ok(files.trash(actorAndProject.actor, request))
        }

        implement(Files.updateAcl) {
            ok(files.updateAcl(actorAndProject.actor, request))
        }
        return@with
    }
}