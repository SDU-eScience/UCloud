package dk.sdu.cloud.plugins

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.config.*
import dk.sdu.cloud.calls.BulkRequest

sealed class OnResourceAllocationResult {
    object ManageThroughUCloud : OnResourceAllocationResult()
    data class ManageThroughProvider(val uniqueId: String) : OnResourceAllocationResult()
}

interface AllocationPlugin : Plugin<ConfigSchema.Plugins.Allocations> {
    suspend fun PluginContext.onResourceAllocation(
        notifications: BulkRequest<DepositNotification>
    ): List<OnResourceAllocationResult> {
        return notifications.items.map { OnResourceAllocationResult.ManageThroughUCloud }
    }
}

