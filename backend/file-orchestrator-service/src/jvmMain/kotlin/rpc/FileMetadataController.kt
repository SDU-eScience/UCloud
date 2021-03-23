package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileMetadata
import dk.sdu.cloud.service.Controller

class FileMetadataController : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileMetadata.create) {
            TODO()
        }

        implement(FileMetadata.delete) {
            TODO()
        }

        implement(FileMetadata.move) {
            TODO()
        }
        return@with
    }

}