package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplates
import dk.sdu.cloud.service.Controller

class FileMetadataTemplateController : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileMetadataTemplates.create) {
            TODO()
        }
        implement(FileMetadataTemplates.browse) {
            TODO()
        }
        implement(FileMetadataTemplates.retrieve) {
            TODO()
        }
        implement(FileMetadataTemplates.deprecate) {
            TODO()
        }
        return@with
    }
}