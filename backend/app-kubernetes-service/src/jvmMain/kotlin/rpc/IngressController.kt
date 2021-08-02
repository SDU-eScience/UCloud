package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.KubernetesIngresses
import dk.sdu.cloud.app.kubernetes.services.IngressService
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkResponseOf
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class IngressController(
    private val ingressService: IngressService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(KubernetesIngresses.create) {
            ingressService.create(request)
            ok(BulkResponse(request.items.map { null }))
        }

        implement(KubernetesIngresses.delete) {
            ingressService.delete(request)
            ok(BulkResponse(request.items.map { Unit }))
        }

        implement(KubernetesIngresses.retrieveProducts) {
            ok(bulkResponseOf(ingressService.support))
        }

        implement(KubernetesIngresses.verify) {
            ingressService.verify(request)
            ok(Unit)
        }

        implement(KubernetesIngresses.updateAcl) {
            ok(BulkResponse(request.items.map {  }))
        }

        return@with
    }
}
