package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.KubernetesNetworkIPMaintenance
import dk.sdu.cloud.app.kubernetes.services.NetworkIPService
import dk.sdu.cloud.app.orchestrator.api.NetworkIPProvider
import dk.sdu.cloud.app.orchestrator.api.NetworkIPSupport
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkResponseOf
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class NetworkIPController(
    private val providerId: String,
    private val service: NetworkIPService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        val networkApi = NetworkIPProvider(providerId)
        implement(networkApi.create) {
            service.create(request)
            ok(BulkResponse(request.items.map { null }))
        }

        implement(networkApi.delete) {
            service.delete(request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(networkApi.verify) {
            ok(Unit)
        }

        implement(networkApi.updateAcl) {
            ok(BulkResponse(request.items.map {  }))
        }

        implement(networkApi.retrieveProducts) {
            ok(bulkResponseOf(
                NetworkIPSupport(
                    service.product,
                    NetworkIPSupport.Firewall(
                        enabled = true
                    )
                )
            ))
        }

        implement(networkApi.updateFirewall) {
            ok(BulkResponse(request.items.map { }))
        }

        val maintenanceApi = KubernetesNetworkIPMaintenance(providerId)
        implement(maintenanceApi.create) {
            ok(service.addToPool(request))
        }

        implement(maintenanceApi.browse) {
            ok(service.browsePool(request.normalize()))
        }

        implement(maintenanceApi.retrieveStatus) {
            ok(service.retrieveStatus())
        }
        return@with
    }
}
