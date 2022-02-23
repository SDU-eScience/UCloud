package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.SyncDeviceProvider
import dk.sdu.cloud.file.orchestrator.api.SyncFolderProvider
import dk.sdu.cloud.file.ucloud.api.*
import dk.sdu.cloud.file.ucloud.services.SyncService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.InternalTokenValidationJWT
import io.ktor.http.*

class SyncController(
    private val providerId: String,
    private val syncService: SyncService,
    private val syncMounterSharedSecret: String?,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        val deviceApi = SyncDeviceProvider(providerId)
        val folderApi = SyncFolderProvider(providerId)
        implement(deviceApi.create) {
            ok(syncService.addDevices(request))
        }

        implement(deviceApi.delete) {
            syncService.removeDevices(request)
            ok(BulkResponse(request.items.map {}))
        }

        implement(folderApi.onPermissionsUpdated) {
            syncService.updatePermissions(request)
            ok(BulkResponse(request.items.map {}))
        }

        implement(deviceApi.updateAcl) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest)
        }

        implement(deviceApi.verify) {
            ok(Unit)
        }

        implement(deviceApi.retrieveProducts) {
            ok(BulkResponse(syncService.syncProducts))
        }

        implement(folderApi.create) {
            ok(syncService.addFolders(request))
        }

        implement(folderApi.delete) {
            syncService.removeFolders(request.items.map { it.id.toLong() })
            ok(BulkResponse(request.items.map { }))
        }

        implement(folderApi.updateAcl) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest)
        }

        implement(folderApi.verify) {
            syncService.verifyFolders(request.items)
            ok(Unit)
        }

        implement(folderApi.retrieveProducts) {
            ok(BulkResponse(syncService.syncProducts))
        }

        implement(UCloudSyncFoldersBrowse(providerId).browse) {
            if (syncMounterSharedSecret == null) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            val validator = InternalTokenValidationJWT.withSharedSecret(syncMounterSharedSecret)
            val bearer = ((ctx as HttpCall).context.request.headers[HttpHeaders.Authorization] ?: "").removePrefix("Bearer ")
            validator.validateOrNull(bearer) ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            ok(syncService.browseFolders(request.device))
        }
    }
}
