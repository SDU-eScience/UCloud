package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.KubernetesIngresses
import dk.sdu.cloud.app.kubernetes.services.IngressService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class IngressController(
    private val ingressService: IngressService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(KubernetesIngresses.create) {
            ingressService.create(request)
            ok(Unit)
        }

        implement(KubernetesIngresses.delete) {
            ingressService.delete(request)
            ok(Unit)
        }

        implement(KubernetesIngresses.retrieveSettings) {
            ok(ingressService.settings)
        }

        implement(KubernetesIngresses.verify) {
            ingressService.verify(request)
            ok(Unit)
        }

        return@with
    }
}