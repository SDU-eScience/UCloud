package dk.sdu.cloud.controllers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.License
import dk.sdu.cloud.app.orchestrator.api.LicenseProvider
import dk.sdu.cloud.app.orchestrator.api.LicenseSupport
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.plugins.LicensePlugin

class LicenseController(
    controllerContext: ControllerContext,
) : BaseResourceController<Product.License, LicenseSupport, License, LicensePlugin, LicenseProvider>(controllerContext) {
    override fun retrievePlugins(): Collection<LicensePlugin> = controllerContext.configuration.plugins.licenses.values
    override fun retrieveApi(providerId: String): LicenseProvider =
        LicenseProvider(controllerContext.configuration.core.providerId)

    override fun RpcServer.configureCustomEndpoints(plugins: Collection<LicensePlugin>, api: LicenseProvider) {
        // None
    }
}
