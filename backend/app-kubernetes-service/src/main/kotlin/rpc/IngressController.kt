package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.services.IngressService
import dk.sdu.cloud.app.orchestrator.api.IngressProvider
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkResponseOf
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class IngressController(
    private val providerId: String,
    private val ingressService: IngressService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        val ingressApi = IngressProvider(providerId)
        implement(ingressApi.create) {
            ingressService.create(request)
            ok(BulkResponse(request.items.map { null }))
        }

        implement(ingressApi.delete) {
            ingressService.delete(request)
            ok(BulkResponse(request.items.map { Unit }))
        }

        implement(ingressApi.retrieveProducts) {
            ok(bulkResponseOf(ingressService.support))
        }

        implement(ingressApi.verify) {
            ingressService.verify(request)
            ok(Unit)
        }

        implement(ingressApi.updateAcl) {
            ok(BulkResponse(request.items.map {  }))
        }

        return@with
    }
}
