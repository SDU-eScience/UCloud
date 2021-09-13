package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.ucloud.api.UCloudSyncDevices
import dk.sdu.cloud.file.ucloud.api.UCloudSyncFolders
import dk.sdu.cloud.file.ucloud.services.SyncService
import dk.sdu.cloud.file.ucloud.services.syncProducts
import dk.sdu.cloud.service.Controller
import io.ktor.http.*

class SyncController(
    private val syncService: SyncService
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
            println("devices verify called")
            ok(Unit)
        }

        implement(UCloudSyncDevices.retrieveProducts) {
            ok(BulkResponse(syncProducts))
        }

        implement(UCloudSyncFolders.create) {
            println("folder create called")
            ok(syncService.addFolders(request))
        }

        /*implement(UCloudSyncFolders.retrieve) {
            println("folder retrieve called")
            ok(syncService.(request))
        }*/


        implement(UCloudSyncFolders.delete) {
            println("folder remove called")
            //syncService.removeFolders(request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(UCloudSyncFolders.updateAcl) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest)
        }

        implement(UCloudSyncFolders.verify) {
            println("folders verify called")
            ok(Unit)
        }

        implement(UCloudSyncFolders.retrieveProducts) {
            ok(BulkResponse(syncProducts))
        }
    }
}
