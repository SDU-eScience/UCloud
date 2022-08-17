package dk.sdu.cloud.plugins

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.config.*
import dk.sdu.cloud.file.orchestrator.api.FSSupport
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.UpdatedAclWithResource

interface FileCollectionPlugin : ResourcePlugin<
    Product.Storage, FSSupport, FileCollection, ConfigSchema.Plugins.FileCollections> {
    override suspend fun PluginContext.create(resource: FileCollection): FindByStringId? {
        throw RPCException("Not supported by this provider", HttpStatusCode.BadRequest)
    }

    override suspend fun PluginContext.delete(resource: FileCollection) {
        throw RPCException("Not supported by this provider", HttpStatusCode.BadRequest)
    }

    override suspend fun PluginContext.runMonitoringLoop() {}
}

abstract class EmptyFileCollectionPlugin : FileCollectionPlugin {
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()

    override suspend fun PluginContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<FSSupport> {
        return BulkResponse(knownProducts.map { FSSupport(it) })
    }

    override suspend fun PluginContext.runMonitoringLoop() {}
    override suspend fun PluginContext.onAllocationComplete(notification: AllocationNotification) {}
    override suspend fun PluginContext.verify(request: BulkRequest<FileCollection>) {}

    override suspend fun PluginContext.create(resource: FileCollection): FindByStringId? = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.delete(resource: FileCollection) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.createBulk(request: BulkRequest<FileCollection>): BulkResponse<FindByStringId?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.deleteBulk(request: BulkRequest<FileCollection>): BulkResponse<Unit?> = throw RPCException("Not supported", HttpStatusCode.BadRequest)
    override suspend fun PluginContext.updateAcl(request: BulkRequest<UpdatedAclWithResource<FileCollection>>) = throw RPCException("Not supported", HttpStatusCode.BadRequest)
}
