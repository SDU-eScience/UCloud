package dk.sdu.cloud.accounting.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.BuildReportResponse
import dk.sdu.cloud.accounting.storage.api.StorageAccountingDescriptions
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.service.Controller
import io.ktor.http.HttpStatusCode

class StorageAccountingController<DBSession>(
    private val storageAccountingService: StorageAccountingService<DBSession>,
    private val cloud: AuthenticatedClient
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(StorageAccountingDescriptions.buildReport) {
            if (ctx.securityPrincipal.username != "_accounting") {
                return@implement error(
                    CommonErrorMessage("User Not Allowed"), HttpStatusCode.Unauthorized
                )
            } else {
                val storageUsed = storageAccountingService
                val homefolder = FileDescriptions.findHomeFolder.call(
                    FindHomeFolderRequest(request.user),
                    cloud
                ).orThrow().path
                return@implement ok(
                    BuildReportResponse(
                        storageUsed.calculateUsage(
                            homefolder,
                            request.user
                        )
                    )
                )

            }
        }
    }
}
