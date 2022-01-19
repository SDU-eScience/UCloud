package dk.sdu.cloud.plugins

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.DepositNotification
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.provider.api.Resource
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.UpdatedAclWithResource

sealed class OnResourceAllocationResult {
    object ManageThroughUCloud : OnResourceAllocationResult()
    data class ManageThroughProvider(val uniqueId: String) : OnResourceAllocationResult()
}

interface ResourcePlugin<P : Product, Sup : ProductSupport, Res : Resource<P, Sup>, ConfigType> : Plugin<ConfigType> {
    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.init
     */
    suspend fun PluginContext.init(owner: ResourceOwner): Unit {}

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.retrieveProducts
     */
    suspend fun PluginContext.retrieveProducts(knownProducts: List<ProductReference>): BulkResponse<Sup>

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.create
     */
    suspend fun PluginContext.createBulk(request: BulkRequest<Res>): BulkResponse<FindByStringId?> {
        return BulkResponse(request.items.map { create(it) })
    }

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.create
     */
    suspend fun PluginContext.create(resource: Res): FindByStringId?

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.delete
     */
    suspend fun PluginContext.deleteBulk(request: BulkRequest<Res>): BulkResponse<Unit?> {
        return BulkResponse(request.items.map { delete(it) })
    }

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.delete
     */
    suspend fun PluginContext.delete(resource: Res)

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.verify
     */
    suspend fun PluginContext.verify(request: BulkRequest<Res>) {
        // NOTE(Dan): The default is to do nothing
    }

    /**
     * @see dk.sdu.cloud.accounting.api.providers.ResourceProviderApi.updateAcl
     */
    suspend fun PluginContext.updateAcl(request: BulkRequest<UpdatedAclWithResource<Res>>) {
        // NOTE(Dan): The default is to do nothing
    }

    suspend fun PluginContext.runMonitoringLoop()

    suspend fun PluginContext.onResourceAllocation(
        notifications: BulkRequest<DepositNotification>
    ): List<OnResourceAllocationResult> {
        return notifications.items.map { OnResourceAllocationResult.ManageThroughUCloud }
    }
}
