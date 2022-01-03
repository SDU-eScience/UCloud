package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkResponseOf
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProviderRenameResponse
import dk.sdu.cloud.file.ucloud.api.UCloudFileCollections
import dk.sdu.cloud.file.ucloud.services.FileCollectionsService
import dk.sdu.cloud.file.ucloud.services.productSupport
import dk.sdu.cloud.service.Controller

class FileCollectionsController(
    private val fileCollections: FileCollectionsService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(UCloudFileCollections.init) {
            fileCollections.init(request.principal)
            ok(Unit)
        }

        implement(UCloudFileCollections.create) {
            fileCollections.create(request)
            ok(BulkResponse(request.items.map { null }))
        }

        implement(UCloudFileCollections.updateAcl) {
            ok(BulkResponse(request.items.map {}))
        }

        implement(UCloudFileCollections.delete) {
            fileCollections.delete(request)
            ok(BulkResponse(request.items.map {}))
        }

        implement(UCloudFileCollections.rename) {
            fileCollections.rename(request)
            ok(BulkResponse(request.items.map {}))
        }

        implement(UCloudFileCollections.retrieveProducts) {
            ok(BulkResponse(productSupport))
        }

        implement(UCloudFileCollections.verify) {
            ok(Unit)
        }
        return@with
    }
}
