package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.KubernetesNetworkIP
import dk.sdu.cloud.app.kubernetes.api.KubernetesNetworkIPMaintenance
import dk.sdu.cloud.app.kubernetes.services.NetworkIPService
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class NetworkIPController(
    private val service: NetworkIPService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(KubernetesNetworkIP.create) {
            ok(service.create(request))
        }

        implement(KubernetesNetworkIP.delete) {
            ok(service.delete(request))
        }

        implement(KubernetesNetworkIP.verify) {
            ok(Unit)
        }

        implement(KubernetesNetworkIP.updateFirewall) {
            ok(Unit)
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
