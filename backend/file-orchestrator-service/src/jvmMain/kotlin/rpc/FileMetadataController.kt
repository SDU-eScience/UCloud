package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileMetadata
import dk.sdu.cloud.file.orchestrator.api.FileMetadataRetrieveAllResponse
import dk.sdu.cloud.file.orchestrator.service.MetadataService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class FileMetadataController(
    private val metadataService: MetadataService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileMetadata.create) {
            ok(metadataService.create(actorAndProject, request))
        }

        implement(FileMetadata.retrieveAll) {
            ok(FileMetadataRetrieveAllResponse(metadataService.retrieveAll(actorAndProject, request.parentPath)))
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
