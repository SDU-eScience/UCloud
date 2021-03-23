package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileCollections
import dk.sdu.cloud.service.Controller

class FileCollectionController : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileCollections.browse) {
            TODO()
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
            TODO()
        }

        implement(FileCollections.updateAcl) {
            TODO()
        }

        return@with
    }
}