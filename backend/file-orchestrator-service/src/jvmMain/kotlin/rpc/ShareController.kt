package dk.sdu.cloud.file.orchestrator.rpc

import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.Shares
import dk.sdu.cloud.file.orchestrator.service.ShareService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class ShareController(
    private val shares: ShareService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Shares.retrieve) {
            ok(shares.retrieve(actorAndProject.actor, request.path))
        }

        implement(Shares.browse) {
            ok(shares.browse(actorAndProject.actor, request.sharedByMe, request.filterPath, request.normalize()))
        }

        implement(Shares.create) {
            ok(shares.create(actorAndProject.actor, request))
        }

        implement(Shares.approve) {
            ok(shares.approve(actorAndProject.actor, request))
        }

        implement(Shares.delete) {
            ok(shares.delete(actorAndProject.actor, request))
        }

        return@with
    }
}
