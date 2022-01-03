package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.app.kubernetes.api.KubernetesIngresses
import dk.sdu.cloud.app.kubernetes.api.KubernetesNetworkIP
import dk.sdu.cloud.app.kubernetes.api.KubernetesNetworkIPMaintenance
import dk.sdu.cloud.app.kubernetes.services.NetworkIPService
import dk.sdu.cloud.app.orchestrator.api.NetworkIPSupport
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkResponseOf
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class NetworkIPController(
    private val service: NetworkIPService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(KubernetesNetworkIP.create) {
            service.create(request)
            ok(BulkResponse(request.items.map { null }))
        }

        implement(KubernetesNetworkIP.delete) {
            service.delete(request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(KubernetesNetworkIP.verify) {
            ok(Unit)
        }

        implement(KubernetesNetworkIP.updateAcl) {
            ok(BulkResponse(request.items.map {  }))
        }

        implement(KubernetesNetworkIP.retrieveProducts) {
            ok(bulkResponseOf(
                NetworkIPSupport(
                    ProductReference("public-ip", "public-ip", UCLOUD_PROVIDER),
                    NetworkIPSupport.Firewall(
                        enabled = true
                    )
                )
            ))
        }

        implement(KubernetesNetworkIP.updateFirewall) {
            ok(BulkResponse(request.items.map { }))
        }

        implement(KubernetesNetworkIPMaintenance.create) {
            ok(service.addToPool(request))
        }

        implement(KubernetesNetworkIPMaintenance.browse) {
            ok(service.browsePool(request.normalize()))
        }

        implement(KubernetesNetworkIPMaintenance.retrieveStatus) {
            ok(service.retrieveStatus())
        }
        return@with
    }
}
