package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.app.orchestrator.api.PublicIPs
import dk.sdu.cloud.app.orchestrator.services.PublicIPService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.async.DBContext

class PublicIPController(
    private val db: DBContext,
    private val publicIps: PublicIPService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(PublicIPs.addToPool) {}
        implement(PublicIPs.removeFromPool) {}

        implement(PublicIPs.applyForAddress) {}
        implement(PublicIPs.approveAddress) {}
        implement(PublicIPs.rejectAddress) {}

        implement(PublicIPs.listAddressApplications) {}
        implement(PublicIPs.listAssignedAddresses) {}
        implement(PublicIPs.listMyAddresses) {}

        implement(PublicIPs.updatePorts) {}
        implement(PublicIPs.releaseAddress) {}
    }
}