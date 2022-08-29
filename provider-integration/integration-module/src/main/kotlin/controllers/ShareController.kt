package dk.sdu.cloud.controllers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.Share
import dk.sdu.cloud.file.orchestrator.api.ShareSupport
import dk.sdu.cloud.file.orchestrator.api.SharesProvider
import dk.sdu.cloud.plugins.SharePlugin

class ShareController(
    controllerContext: ControllerContext,
) : BaseResourceController<Product.Storage, ShareSupport, Share, SharePlugin, SharesProvider>(controllerContext) {
    override fun retrievePlugins(): Collection<SharePlugin> = controllerContext.configuration.plugins.shares.values

    override fun retrieveApi(providerId: String): SharesProvider =
        SharesProvider(controllerContext.configuration.core.providerId)

    override fun RpcServer.configureCustomEndpoints(plugins: Collection<SharePlugin>, api: SharesProvider) {
        // Nothing here
    }
}
