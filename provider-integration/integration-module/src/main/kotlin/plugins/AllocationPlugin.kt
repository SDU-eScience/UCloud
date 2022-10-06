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
data class DetailedAllocationNotification(
    var balance: Long,
    val owner: ResourceOwnerWithId,
    val allocationId: String,
    val productCategory: String,
    val productType: ProductType,
)

@Serializable
data class AllocationNotification(
    var balance: Long,
    val owner: ResourceOwnerWithId,
    val productCategory: String,
    val productType: ProductType,
)

interface AllocationPlugin : Plugin<ConfigSchema.Plugins.Allocations> {
    suspend fun PluginContext.onResourceAllocation(
        notifications: List<AllocationNotification>
    ): List<OnResourceAllocationResult> {
        return notifications.map { OnResourceAllocationResult.ManageThroughUCloud }
    }

    suspend fun PluginContext.onResourceAllocationDetailed(
        notifications: List<DetailedAllocationNotification>
    ): List<OnResourceAllocationResult> {
        return notifications.map { OnResourceAllocationResult.ManageThroughUCloud }
    }

    suspend fun PluginContext.onResourceSynchronization(
        notifications: List<AllocationNotification>
    ) {
        // Do nothing
    }

    suspend fun PluginContext.onResourceSynchronizationDetailed(
        notifications: List<DetailedAllocationNotification>
    ) {
        // Do nothing
    }
}
