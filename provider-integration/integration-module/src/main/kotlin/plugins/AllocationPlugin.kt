package dk.sdu.cloud.plugins

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.controllers.ResourceOwnerWithId
import kotlinx.serialization.*

@Serializable
sealed class OnResourceAllocationResult {
    @Serializable
    @SerialName("ucloud_managed")
    object ManageThroughUCloud : OnResourceAllocationResult()

    @Serializable
    @SerialName("provider_managed")
    data class ManageThroughProvider(val uniqueId: String) : OnResourceAllocationResult()
}

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
    ): List<OnResourceAllocationResult> {
        return notifications.map { OnResourceAllocationResult.ManageThroughUCloud }
    }

    suspend fun PluginContext.onResourceAllocationSingle(
        notifications: List<AllocationNotificationSingle>
    ): List<OnResourceAllocationResult> {
        return notifications.map { OnResourceAllocationResult.ManageThroughUCloud }
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
