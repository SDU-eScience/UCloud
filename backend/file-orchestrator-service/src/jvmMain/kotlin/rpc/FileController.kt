package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.service.Controller

class FileController : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Files.browse) {
            TODO()
        }

        implement(Files.copy) {
            TODO()
        }

        implement(Files.createDownload) {
            TODO()
        }

        implement(Files.createUpload) {
            TODO()
        }

        implement(Files.createFolder) {
            TODO()
        }

        implement(Files.delete) {
            TODO()
        }

        implement(Files.move) {
            TODO()
        }

        implement(Files.retrieve) {
            TODO()
        }

        implement(Files.trash) {
            TODO()
        }

        implement(Files.updateAcl) {
            TODO()
        }
        return@with
    }
}