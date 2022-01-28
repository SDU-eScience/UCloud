package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.ucloud.api.*
import dk.sdu.cloud.file.ucloud.services.SyncService
import dk.sdu.cloud.file.ucloud.services.syncProducts
import dk.sdu.cloud.service.Controller

class SyncController(
    private val syncService: SyncService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(UCloudSyncDevices.create) {
            ok(syncService.addDevices(request))
        }

        implement(UCloudSyncDevices.delete) {
            syncService.removeDevices(request)
            ok(BulkResponse(request.items.map {}))
        }

        implement(UCloudSyncDevices.updateAcl) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest)
        }

        implement(UCloudSyncDevices.verify) {
            ok(Unit)
        }

        implement(UCloudSyncDevices.retrieveProducts) {
            ok(BulkResponse(syncProducts))
        }

        implement(UCloudSyncFolders.create) {
            ok(syncService.addFolders(request))
        }

        implement(UCloudSyncFolders.delete) {
            syncService.removeFolders(request.items.map { it.id.toLong() })
            ok(BulkResponse(request.items.map { }))
        }

        implement(UCloudSyncFolders.updateAcl) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest)
        }

        implement(UCloudSyncFolders.verify) {
            ok(Unit)
        }

        implement(UCloudSyncFolders.retrieveProducts) {
            ok(BulkResponse(syncProducts))
        }

        implement(UCloudSyncFoldersBrowse.browse) {
            ok(syncService.browseFolders(request.device))
        }
    }
}
