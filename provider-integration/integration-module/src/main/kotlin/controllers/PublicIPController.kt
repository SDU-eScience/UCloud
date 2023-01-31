package dk.sdu.cloud.controllers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.Ingress
import dk.sdu.cloud.app.orchestrator.api.IngressProvider
import dk.sdu.cloud.app.orchestrator.api.IngressSupport
import dk.sdu.cloud.app.orchestrator.api.NetworkIP
import dk.sdu.cloud.app.orchestrator.api.NetworkIPProvider
import dk.sdu.cloud.app.orchestrator.api.NetworkIPSupport
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.plugins.IngressPlugin
import dk.sdu.cloud.plugins.PublicIPPlugin

class PublicIPController(
    controllerContext: ControllerContext
) : BaseResourceController<Product.NetworkIP, NetworkIPSupport, NetworkIP, PublicIPPlugin, NetworkIPProvider>(
    controllerContext
) {
    override fun retrievePlugins() = controllerContext.configuration.plugins.publicIps.values
    override fun retrieveApi(providerId: String) = NetworkIPProvider(providerId)

    override fun RpcServer.configureCustomEndpoints(plugins: Collection<PublicIPPlugin>, api: NetworkIPProvider) {
        implement(api.updateFirewall) {
            ok(BulkResponse(request.items.map { }))
        }
    }
}
