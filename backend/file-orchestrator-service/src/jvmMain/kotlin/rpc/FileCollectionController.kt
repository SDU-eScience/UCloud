package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.accounting.util.asController
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileCollections
import dk.sdu.cloud.file.orchestrator.service.FileCollectionService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class FileCollectionController(
    private val fileCollections: FileCollectionService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        fileCollections.asController().configure(rpcServer)
        implement(FileCollections.rename) {
            ok(fileCollections.rename(actorAndProject, request))
        }

        return@with
    }
}