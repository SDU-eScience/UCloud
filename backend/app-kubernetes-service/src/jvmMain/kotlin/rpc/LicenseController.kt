package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.app.kubernetes.api.KubernetesLicenseMaintenance
import dk.sdu.cloud.app.kubernetes.services.LicenseService
import dk.sdu.cloud.app.orchestrator.api.LicenseProvider
import dk.sdu.cloud.app.orchestrator.api.LicenseSupport
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.service.Controller

class LicenseController(
    private val providerId: String,
    private val service: LicenseService,
) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        val licenseApi = LicenseProvider(providerId)
        implement(licenseApi.create) {
            service.createInstance(request)
            ok(BulkResponse(request.items.map { null }))
        }

        implement(licenseApi.delete) {
            service.deleteInstance(request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(licenseApi.verify) {
            ok(Unit)
        }

        implement(licenseApi.retrieveProducts) {
            ok(BulkResponse(
                service.fetchAllSupportedProducts().map {
                    LicenseSupport(ProductReference(it.name, it.category.name, it.category.provider))
                }
            ))
        }

        implement(licenseApi.updateAcl) {
            ok(BulkResponse(request.items.map {}))
        }

        val maintenanceApi = KubernetesLicenseMaintenance(providerId)
        implement(maintenanceApi.create) {
            service.createServer(request)
            ok(Unit)
        }

        implement(maintenanceApi.browse) {
            ok(service.browseServers(request))
        }

        implement(maintenanceApi.update) {
            service.updateServer(request)
            ok(Unit)
        }

        return@with
    }
}
