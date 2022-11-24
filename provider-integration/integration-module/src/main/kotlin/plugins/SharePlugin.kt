package dk.sdu.cloud.plugins

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProviderRegisteredResource
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.file.orchestrator.api.Share
import dk.sdu.cloud.file.orchestrator.api.ShareSupport
import dk.sdu.cloud.file.orchestrator.api.ShareType
import dk.sdu.cloud.file.orchestrator.api.SharesControl
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.provider.api.UpdatedAclWithResource
import dk.sdu.cloud.service.Time

data class ConfiguredShare(
    val name: String,
    val product: ProductReferenceWithoutProvider,
    val collectionId: String,
)

// NOTE(Dan): I am copying & pasting the following warning from the documentation. This is why the SharePlugin is
// disabling almost every single overridable method available.
//
// This feature is currently implemented for backwards compatibility with UCloud. We don't currently recommend
// other providers implement this functionality. We generally recommend that you use a full-blown project for
// collaboration.

abstract class SharePlugin : ResourcePlugin<Product.Storage, ShareSupport, Share, ConfigSchema.Plugins.Shares> {
    override var pluginName: String = "Unknown"
    override var productAllocation: List<ProductReferenceWithoutProvider> = emptyList()
    override var productAllocationResolved: List<Product> = emptyList()

    // NOTE(Dan): This could change in the future if we want it to.
    override fun supportsServiceUserMode(): Boolean = true
    override fun supportsRealUserMode(): Boolean = false

    final override suspend fun RequestContext.initInUserMode(owner: ResourceOwner) {
        // Do nothing
    }

    final override suspend fun PluginContext.onAllocationCompleteInServerModeTotal(notification: AllocationNotificationTotal) {
        // Do nothing
    }

    final override suspend fun RequestContext.retrieveProducts(
        knownProducts: List<ProductReference>
    ): BulkResponse<ShareSupport> {
        return BulkResponse(knownProducts.map { ShareSupport(ShareType.UCLOUD_MANAGED_COLLECTION, it) })
    }

    final override suspend fun RequestContext.createBulk(request: BulkRequest<Share>): BulkResponse<FindByStringId?> {
        for (reqItem in request.items) create(reqItem)
        return BulkResponse(request.items.map { null })
    }

    final override suspend fun RequestContext.create(resource: Share): FindByStringId? {
        val createdShare = onCreate(resource)
        val collectionId = FileCollectionsControl.register.call(
            bulkRequestOf(
                ProviderRegisteredResource(
                    FileCollection.Spec(
                        createdShare.name,
                        ProductReference(createdShare.product.id, createdShare.product.category, config.core.providerId)
                    ),
                    createdShare.collectionId,
                    createdBy = "_ucloud",
                    project = null,
                )
            ),
            rpcClient
        ).orThrow().responses.single()

        SharesControl.update.call(
            bulkRequestOf(
                ResourceUpdateAndId(
                    resource.id,
                    Share.Update(
                        newState = Share.State.PENDING,
                        shareAvailableAt = "/${collectionId.id}",
                        timestamp = Time.now(),
                        status = null
                    )
                )
            ),
            rpcClient
        ).orThrow()

        return null
    }

    protected abstract suspend fun RequestContext.onCreate(resource: Share): ConfiguredShare

    final override suspend fun RequestContext.deleteBulk(request: BulkRequest<Share>): BulkResponse<Unit?> {
        // Do nothing
        return BulkResponse(request.items.map { null })
    }

    final override suspend fun RequestContext.delete(resource: Share) {
        // Do nothing
    }

    final override suspend fun RequestContext.verify(request: BulkRequest<Share>) {
        // Do nothing
    }

    final override suspend fun RequestContext.updateAcl(request: BulkRequest<UpdatedAclWithResource<Share>>) {
        // Do nothing
    }

    final override suspend fun PluginContext.runMonitoringLoopInUserMode() {
        // Do nothing
    }

    final override suspend fun PluginContext.runMonitoringLoopInServerMode() {
        // Do nothing
    }
}
