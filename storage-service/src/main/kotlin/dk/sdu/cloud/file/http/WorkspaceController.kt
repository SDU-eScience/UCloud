package dk.sdu.cloud.file.http

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.api.WorkspaceDescriptions
import dk.sdu.cloud.file.api.Workspaces
import dk.sdu.cloud.file.services.WorkspaceService
import dk.sdu.cloud.service.Controller

class WorkspaceController(
    private val workspaceService: WorkspaceService
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(WorkspaceDescriptions.create) {
            val response = workspaceService.create(
                request.username,
                request.mounts,
                request.allowFailures,
                request.createSymbolicLinkAt
            )
            ok(Workspaces.Create.Response(response.workspaceId, response.failures))
        }

        implement(WorkspaceDescriptions.delete) {
            workspaceService.delete(request.workspaceId)
            ok(Workspaces.Delete.Response)
        }

        implement(WorkspaceDescriptions.transfer) {
            val transferredFiles =
                workspaceService.transfer(
                    request.username,
                    request.workspaceId,
                    request.transferGlobs,
                    request.destination
                )

            if (request.deleteWorkspace) {
                workspaceService.delete(request.workspaceId)
            }

            ok(Workspaces.Transfer.Response(transferredFiles))
        }

        return@with
    }
}
