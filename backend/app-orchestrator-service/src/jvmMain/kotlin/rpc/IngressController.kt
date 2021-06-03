package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.app.orchestrator.api.Ingresses
import dk.sdu.cloud.app.orchestrator.services.IngressService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class IngressController(
    private val ingressService: IngressService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Ingresses.browse) {}
        implement(Ingresses.create) {}
        implement(Ingresses.retrieve) {}
        implement(Ingresses.retrieveProducts) {}
        implement(Ingresses.delete) {}
        implement(Ingresses.updateAcl) {}
        return@with
    }
}
