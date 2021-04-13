package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileCollections
import dk.sdu.cloud.file.orchestrator.service.FileCollectionService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class FileCollectionController(
    private val fileCollections: FileCollectionService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileCollections.browse) {
            ok(fileCollections.browse(actorAndProject, request))
        }

        implement(FileCollections.create) {
            TODO()
        }

        implement(FileCollections.delete) {
            TODO()
        }

        implement(FileCollections.rename) {
            TODO()
        }

        implement(FileCollections.retrieve) {
            ok(fileCollections.retrieve(actorAndProject, request))
        }

        implement(FileCollections.updateAcl) {
            TODO()
        }

        return@with
    }
}