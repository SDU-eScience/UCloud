package dk.sdu.cloud.plugins

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.controllers.ResourceOwnerWithId
import kotlinx.serialization.*

@Serializable
data class AllocationNotificationSingle(
    var balance: Long,
    val owner: ResourceOwnerWithId,
    val allocationId: String,
    val productCategory: String,
    val productType: ProductType,
)

@Serializable
data class AllocationNotificationTotal(
    var balance: Long,
    val owner: ResourceOwnerWithId,
    val productCategory: String,
    val productType: ProductType,
)

interface AllocationPlugin : Plugin<ConfigSchema.Plugins.Allocations> {
    suspend fun PluginContext.onResourceAllocationTotal(
        notifications: List<AllocationNotificationTotal>
    ) {

    }

    suspend fun PluginContext.onResourceAllocationSingle(
        notifications: List<AllocationNotificationSingle>
    ) {
    }

    suspend fun PluginContext.onResourceSynchronizationTotal(
        notifications: List<AllocationNotificationTotal>
    ) {
        // Do nothing
    }

    suspend fun PluginContext.onResourceSynchronizationSingle(
        notifications: List<AllocationNotificationSingle>
    ) {
        // Do nothing
    }
}
