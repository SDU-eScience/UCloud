package dk.sdu.cloud.controllers

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.FSSupport
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.plugins.FileCollectionPlugin

class FileCollectionController(
    controllerContext: ControllerContext
) : BaseResourceController<Product.Storage, FSSupport, FileCollection, FileCollectionPlugin, FileCollectionsProvider>(
    controllerContext
) {
    override fun retrievePlugins() = controllerContext.configuration.plugins.fileCollections.values
    override fun retrieveApi(providerId: String): FileCollectionsProvider = FileCollectionsProvider(providerId)

    override fun RpcServer.configureCustomEndpoints(
        plugins: Collection<FileCollectionPlugin>,
        api: FileCollectionsProvider
    ) {
        // TODO Add rename here
    }
}
