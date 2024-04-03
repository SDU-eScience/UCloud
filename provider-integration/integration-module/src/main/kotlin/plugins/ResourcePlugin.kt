package dk.sdu.cloud.plugins

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.config.ProductReferenceWithoutProvider
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.ProductV2
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.provider.api.Resource
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.UpdatedAclWithResource

interface ResourcePlugin<P : Product, Sup : ProductSupport, Res : Resource<P, Sup>, ConfigType> : Plugin<ConfigType> {
    var pluginName: String
    var productAllocation: List<ProductReferenceWithoutProvider>
    var productAllocationResolved: List<ProductV2>

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.init
     */
    suspend fun RequestContext.initInUserMode(owner: ResourceOwner): Unit {}

    /**
     * Invoked by the notification controller _after_ the allocation plugin has run. This method is run in the server
     * context. If multiple plugins cover the same category then _all_ plugins will be invoked.
     *
     * Each allocation notification contains the total balance for the product across allocations.
     *
     * This method is only invoked due to synchronization. There is no guarantee that anything has changed in the
     * wallet.
     */
    suspend fun PluginContext.onWalletSynchronized(notification: AllocationPlugin.Message) {}

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.retrieveProducts
     */
    suspend fun RequestContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<Sup>

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.create
     */
    suspend fun RequestContext.createBulk(request: BulkRequest<Res>): BulkResponse<FindByStringId?> {
        return BulkResponse(request.items.map { create(it) })
    }

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.create
     */
    suspend fun RequestContext.create(resource: Res): FindByStringId?

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.delete
     */
    suspend fun RequestContext.deleteBulk(request: BulkRequest<Res>): BulkResponse<Unit?> {
        return BulkResponse(request.items.map { delete(it) })
    }

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.delete
     */
    suspend fun RequestContext.delete(resource: Res)

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.verify
     */
    suspend fun RequestContext.verify(request: BulkRequest<Res>) {
        // NOTE(Dan): The default is to do nothing
    }

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.updateAcl
     */
    suspend fun RequestContext.updateAcl(request: BulkRequest<UpdatedAclWithResource<Res>>) {
        // NOTE(Dan): The default is to do nothing
    }

    suspend fun PluginContext.runMonitoringLoopInUserMode() {}
    suspend fun PluginContext.runMonitoringLoopInServerMode() {}
}

