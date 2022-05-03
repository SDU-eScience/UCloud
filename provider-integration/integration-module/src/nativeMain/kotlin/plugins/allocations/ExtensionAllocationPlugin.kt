package dk.sdu.cloud.plugins.allocations

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.config.*
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.plugins.AllocationPlugin

class ExtensionAllocationPlugin : AllocationPlugin {
    private lateinit var pluginConfig: ExtensionAllocationConfig

    override fun configure(config: ConfigSchema.Plugins.Allocations) {
        this.pluginConfig = config as ExtensionAllocationConfig
    }

    override suspend fun PluginContext.onResourceAllocation(
        notifications: BulkRequest<DepositNotification>
    ): List<OnResourceAllocationResult> {
        TODO()
    }

    private companion object Extensions {
        val onAllocation = extension<DepositNotification, OnResourceAllocationResult>()
    }
}

