package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.file.ucloud.services.FileCollectionsService
import dk.sdu.cloud.service.Controller

class FileCollectionsController(
    private val providerId: String,
    private val fileCollections: FileCollectionsService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        val collectionApi = FileCollectionsProvider(providerId)
        implement(collectionApi.init) {
            fileCollections.init(request.principal)
            ok(Unit)
        }

        implement(collectionApi.create) {
            fileCollections.create(request)
            ok(BulkResponse(request.items.map { null }))
        }

        implement(collectionApi.updateAcl) {
            ok(BulkResponse(request.items.map {}))
        }

        implement(collectionApi.delete) {
            fileCollections.delete(request)
            ok(BulkResponse(request.items.map {}))
        }

        implement(collectionApi.rename) {
            fileCollections.rename(request)
            ok(BulkResponse(request.items.map {}))
        }

        implement(collectionApi.retrieveProducts) {
            ok(BulkResponse(fileCollections.productSupport))
        }

        implement(collectionApi.verify) {
            ok(Unit)
        }
        return@with
    }
}
