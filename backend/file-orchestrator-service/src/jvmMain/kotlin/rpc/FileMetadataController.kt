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
            ok(FileMetadataRetrieveAllResponse(metadataService.retrieveAll(actorAndProject, request.fileId)))
        }

        implement(FileMetadata.delete) {
            metadataService.delete(actorAndProject, request)
            ok(Unit)
        }

        implement(FileMetadata.move) {
            metadataService.move(actorAndProject, request)
            ok(Unit)
        }

        implement(FileMetadata.approve) {
            metadataService.approve(actorAndProject, request)
            ok(Unit)
        }

        implement(FileMetadata.reject) {
            metadataService.reject(actorAndProject, request)
            ok(Unit)
        }

        implement(FileMetadata.browse) {
            ok(metadataService.browse(actorAndProject, request))
        }

        return@with
    }
}
