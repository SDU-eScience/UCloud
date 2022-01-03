package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkResponseOf
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.ShareSupport
import dk.sdu.cloud.file.orchestrator.api.ShareType
import dk.sdu.cloud.file.ucloud.api.UCloudShares
import dk.sdu.cloud.file.ucloud.services.PathConverter
import dk.sdu.cloud.file.ucloud.services.ShareService
import dk.sdu.cloud.service.Controller

class ShareController(
    private val shares: ShareService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(UCloudShares.create) {
            ok(shares.create(request))
        }

        implement(UCloudShares.delete) {
            ok(BulkResponse(request.items.map { Unit }))
        }

        implement(UCloudShares.verify) {
            ok(Unit)
        }

        implement(UCloudShares.retrieveProducts) {
            ok(bulkResponseOf(ShareSupport(ShareType.UCLOUD_MANAGED_COLLECTION, PathConverter.PRODUCT_REFERENCE)))
        }

        implement(UCloudShares.updateAcl) {
            ok(BulkResponse(request.items.map { Unit }))
        }
        return@with
    }

}
