package dk.sdu.cloud.plugins

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.ProductBasedConfiguration
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.FSSupport
import dk.sdu.cloud.file.orchestrator.api.FileCollection

interface FileCollectionPlugin : ResourcePlugin<Product.Storage, FSSupport, FileCollection, ProductBasedConfiguration> {
    override suspend fun PluginContext.create(resource: FileCollection): FindByStringId? {
        throw RPCException("Not supported by this provider", HttpStatusCode.BadRequest)
    }

    override suspend fun PluginContext.delete(resource: FileCollection) {
        throw RPCException("Not supported by this provider", HttpStatusCode.BadRequest)
    }

    override suspend fun PluginContext.runMonitoringLoop() {}
}