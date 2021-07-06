package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.app.kubernetes.api.KubernetesLicenseMaintenance
import dk.sdu.cloud.app.kubernetes.api.KubernetesLicenses
import dk.sdu.cloud.app.kubernetes.services.LicenseService
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class LicenseController(
    private val service: LicenseService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(KubernetesLicenses.create) {
            service.createInstance(request)
            ok(BulkResponse(request.items.map { null }))
        }

        implement(KubernetesLicenses.delete) {
            service.deleteInstance(request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(KubernetesLicenses.verify) {
            ok(Unit)
        }

        implement(KubernetesLicenses.retrieveProducts) {
            ok(BulkResponse(emptyList())) // TODO
        }

        implement(KubernetesLicenses.updateAcl) {
            ok(BulkResponse(request.items.map {}))
        }

        implement(KubernetesLicenseMaintenance.create) {
            service.createServer(request)
            ok(Unit)
        }

        implement(KubernetesLicenseMaintenance.browse) {
            ok(service.browseServers(request))
        }

        implement(KubernetesLicenseMaintenance.update) {
            service.updateServer(request)
            ok(Unit)
        }

        return@with
    }
}
