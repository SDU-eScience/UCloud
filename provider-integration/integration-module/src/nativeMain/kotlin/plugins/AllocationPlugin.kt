package dk.sdu.cloud.plugins

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.config.*
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
data class AllocationNotification(
   val balance: Long,
   val owner: ResourceOwnerWithId,
   val allocationId: String,
   val productCategory: String,
   val productType: ProductType,
)

// TODO(Dan): Move this to a different file
@Serializable
sealed class ResourceOwnerWithId {
    @Serializable
    @SerialName("user")
    data class User(val username: String, val uid: Int) : ResourceOwnerWithId()

    @Serializable
    @SerialName("project")
    data class Project(val projectId: String, val gid: Int) : ResourceOwnerWithId()
}

interface AllocationPlugin : Plugin<ConfigSchema.Plugins.Allocations> {
    suspend fun PluginContext.onResourceAllocation(
        notifications: List<AllocationNotification>
    ): List<OnResourceAllocationResult> {
        return notifications.map { OnResourceAllocationResult.ManageThroughUCloud }
    }

    suspend fun PluginContext.onResourceSynchronization(
        notifications: List<AllocationNotification>
    ) {
        // Do nothing
    }
}
