package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplates
import dk.sdu.cloud.file.orchestrator.service.MetadataTemplates
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class FileMetadataTemplateController(
    private val metadataTemplates: MetadataTemplates,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(FileMetadataTemplates.create) {
            metadataTemplates.create(actorAndProject, request)
            ok(BulkResponse(request.items.map { FindByStringId(it.id) }))
        }

        implement(FileMetadataTemplates.browse) {
            ok(metadataTemplates.browse(actorAndProject, request.normalize()))
        }

        implement(FileMetadataTemplates.retrieve) {
            ok(metadataTemplates.retrieve(actorAndProject, request))
        }

        implement(FileMetadataTemplates.deprecate) {
            metadataTemplates.deprecate(actorAndProject, request)
            ok(Unit)
        }

        return@with
    }
}