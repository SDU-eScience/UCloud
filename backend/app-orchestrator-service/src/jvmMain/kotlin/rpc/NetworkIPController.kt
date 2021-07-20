package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.accounting.util.asController
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.NetworkIPService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.project
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject
import dk.sdu.cloud.toActor

class NetworkIPController(
    private val service: NetworkIPService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        service.asController().configure(rpcServer)
        implement(NetworkIPs.updateFirewall) {
            ok(service.updateFirewall(actorAndProject, request))
        }
        return@with
    }
}
