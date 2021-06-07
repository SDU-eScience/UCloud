package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.app.orchestrator.api.IngressControl
import dk.sdu.cloud.app.orchestrator.api.Ingresses
import dk.sdu.cloud.app.orchestrator.services.IngressService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class IngressController(
    private val ingressService: IngressService
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(Ingresses.browse) {
            ok(ingressService.browse(actorAndProject, request, request.flags))
        }
        implement(Ingresses.create) {
            ok(ingressService.create(actorAndProject, request))
        }
        implement(Ingresses.retrieve) {}
        implement(Ingresses.retrieveProducts) {
            ok(ingressService.retrieveProducts(actorAndProject))
        }
        implement(IngressControl.update) {
            ok(ingressService.addUpdate(actorAndProject, request))
        }
        implement(Ingresses.delete) {}
        implement(Ingresses.updateAcl) {}
        return@with
    }
}
