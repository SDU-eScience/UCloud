package dk.sdu.cloud.controllers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.Ingress
import dk.sdu.cloud.app.orchestrator.api.IngressProvider
import dk.sdu.cloud.app.orchestrator.api.IngressSupport
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.plugins.IngressPlugin

class IngressController(
    controllerContext: ControllerContext
) : BaseResourceController<Product.Ingress, IngressSupport, Ingress, IngressPlugin, IngressProvider>(
    controllerContext
) {
    override fun retrievePlugins() = controllerContext.configuration.plugins.ingresses.values
    override fun retrieveApi(providerId: String) = IngressProvider(providerId)

    override fun RpcServer.configureCustomEndpoints(plugins: Collection<IngressPlugin>, api: IngressProvider) {

    }
}
