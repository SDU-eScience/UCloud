package dk.sdu.cloud.plugins.allocations

import dk.sdu.cloud.config.*
import dk.sdu.cloud.plugins.*

class ExtensionAllocationPlugin : AllocationPlugin {
    private lateinit var pluginConfig: ExtensionAllocationConfig

    override fun configure(config: ConfigSchema.Plugins.Allocations) {
        this.pluginConfig = config as ExtensionAllocationConfig
    }

    override suspend fun PluginContext.onResourceAllocation(
        notifications: List<AllocationNotification>
    ): List<OnResourceAllocationResult> {
        val results = ArrayList<OnResourceAllocationResult>()
        for (notification in notifications) {
            results.add(onAllocation.invoke(pluginConfig.extensions.onAllocation.value, notification))
        }
        return results
    }

    override suspend fun PluginContext.onResourceSynchronization(notifications: List<AllocationNotification>) {
        for (notification in notifications) {
            onSynchronization.invoke(pluginConfig.extensions.onSynchronization.value, notification)
        }
    }

    private companion object Extensions {
        val onAllocation = extension<AllocationNotification, OnResourceAllocationResult>()
        val onSynchronization = extension<AllocationNotification, Unit>()
    }
}
