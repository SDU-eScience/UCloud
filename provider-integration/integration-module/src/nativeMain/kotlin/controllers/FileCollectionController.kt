package dk.sdu.cloud.controllers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.althttp.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FSSupport
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.plugins.FileCollectionPlugin
import dk.sdu.cloud.plugins.ProductBasedPlugins

class FileCollectionController(
    controllerContext: ControllerContext
) : BaseResourceController<Product.Storage, FSSupport, FileCollection, FileCollectionPlugin, FileCollectionsProvider>(
    controllerContext
) {
    override fun retrievePlugins(): ProductBasedPlugins<FileCollectionPlugin>? =
        controllerContext.plugins.fileCollection

    override fun retrieveApi(providerId: String): FileCollectionsProvider =
        FileCollectionsProvider(providerId)

    override fun RpcServer.configureCustomEndpoints(
        plugins: ProductBasedPlugins<FileCollectionPlugin>,
        api: FileCollectionsProvider
    ) {
        // TODO Add rename here
    }
}
