package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkResponseOf
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.ShareSupport
import dk.sdu.cloud.file.orchestrator.api.ShareType
import dk.sdu.cloud.file.orchestrator.api.SharesProvider
import dk.sdu.cloud.file.ucloud.services.PathConverter
import dk.sdu.cloud.file.ucloud.services.ShareService
import dk.sdu.cloud.service.Controller

class ShareController(
    private val providerId: String,
    private val pathConverter: PathConverter,
    private val shares: ShareService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        val sharesApi = SharesProvider(providerId)
        implement(sharesApi.create) {
            ok(shares.create(request))
        }

        implement(sharesApi.delete) {
            ok(BulkResponse(request.items.map { Unit }))
        }

        implement(sharesApi.verify) {
            ok(Unit)
        }

        implement(sharesApi.retrieveProducts) {
            ok(bulkResponseOf(ShareSupport(ShareType.UCLOUD_MANAGED_COLLECTION, pathConverter.productReference)))
        }

        implement(sharesApi.updateAcl) {
            ok(BulkResponse(request.items.map { Unit }))
        }
        return@with
    }

}
