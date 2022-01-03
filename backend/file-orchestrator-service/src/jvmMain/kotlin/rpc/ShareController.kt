package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.accounting.util.asController
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.Shares
import dk.sdu.cloud.file.orchestrator.service.ShareService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class ShareController(
    private val shares: ShareService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        shares.asController().configure(rpcServer)

        implement(Shares.approve) {
            ok(shares.approve(actorAndProject, request))
        }

        implement(Shares.reject) {
            ok(shares.reject(actorAndProject, request))
        }

        implement(Shares.browseOutgoing) {
            ok(shares.browseOutgoing(actorAndProject, request))
        }

        implement(Shares.updatePermissions) {
            ok(shares.updatePermissions(actorAndProject, request))
        }

        return@with
    }
}
