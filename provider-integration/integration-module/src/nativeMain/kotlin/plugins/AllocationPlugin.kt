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
   val owner: Owner,
   val allocationId: String,
   val productCategory: String,
   val productType: ProductType,
) {
    @Serializable
    sealed class Owner {
        @Serializable
        @SerialName("user")
        data class User(val username: String, val uid: Int) : Owner()

        @Serializable
        @SerialName("project")
        data class Project(val projectId: String, val gid: Int) : Owner()
    }
}

interface AllocationPlugin : Plugin<ConfigSchema.Plugins.Allocations> {
    suspend fun PluginContext.onResourceAllocation(
        notifications: List<AllocationNotification>
    ): List<OnResourceAllocationResult> {
        return notifications.map { OnResourceAllocationResult.ManageThroughUCloud }
    }
}

