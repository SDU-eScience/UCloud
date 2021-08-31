package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.SyncFolderSupport
import dk.sdu.cloud.file.ucloud.api.UCloudSyncDevices
import dk.sdu.cloud.file.ucloud.api.UCloudSyncFolders
import dk.sdu.cloud.service.Controller
import io.ktor.http.*

class SyncController : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(UCloudSyncDevices.create) {
            ok(BulkResponse(request.items.map { null }))
        }

        implement(UCloudSyncDevices.delete) {
            ok(BulkResponse(request.items.map { Unit }))
        }

        implement(UCloudSyncDevices.updateAcl) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest)
        }

        implement(UCloudSyncDevices.verify) {
            ok(Unit)
        }

        implement(UCloudSyncDevices.retrieveProducts) {
            ok(BulkResponse(products))
        }

        implement(UCloudSyncFolders.create) {
            ok(BulkResponse(request.items.map { null }))
        }

        implement(UCloudSyncFolders.delete) {
            ok(BulkResponse(request.items.map { Unit }))
        }

        implement(UCloudSyncFolders.updateAcl) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest)
        }

        implement(UCloudSyncFolders.verify) {
            ok(Unit)
        }

        implement(UCloudSyncFolders.retrieveProducts) {
            ok(BulkResponse(products))
        }
    }
}

val products = listOf(
    SyncFolderSupport(ProductReference("u1-sync", "u1-sync", UCLOUD_PROVIDER))
)