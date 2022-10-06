package dk.sdu.cloud.plugins.allocations

import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.plugins.*
import kotlinx.serialization.builtins.serializer

class ExtensionAllocationPlugin : AllocationPlugin {
    override val pluginTitle: String = "Extension"
    private lateinit var pluginConfig: ConfigSchema.Plugins.Allocations.Extension

    override fun configure(config: ConfigSchema.Plugins.Allocations) {
        this.pluginConfig = config as ConfigSchema.Plugins.Allocations.Extension
    }

    override suspend fun PluginContext.onResourceAllocation(
        notifications: List<AllocationNotification>
    ): List<OnResourceAllocationResult> {
        val results = ArrayList<OnResourceAllocationResult>()
        for (notification in notifications) {
            results.add(onAllocation.invoke(pluginConfig.extensions.onAllocation, notification))
        }
        return results
    }

    override suspend fun PluginContext.onResourceAllocationDetailed(
        notifications: List<DetailedAllocationNotification>
    ): List<OnResourceAllocationResult> {
        val results = ArrayList<OnResourceAllocationResult>()
        for (notification in notifications) {
            results.add(onAllocationDetailed.invoke(pluginConfig.extensions.onAllocationDetailed, notification))
        }
        return results
    }

    override suspend fun PluginContext.onResourceSynchronization(notifications: List<AllocationNotification>) {
        for (notification in notifications) {
            onSynchronization.invoke(pluginConfig.extensions.onSynchronization, notification)
        }
    }

    override suspend fun PluginContext.onResourceSynchronizationDetailed(notifications: List<DetailedAllocationNotification>) {
        for (notification in notifications) {
            onSynchronizationDetailed.invoke(pluginConfig.extensions.onSynchronizationDetailed, notification)
        }
    }

    private companion object Extensions {
        val onAllocation = extension(AllocationNotification.serializer(), OnResourceAllocationResult.serializer())
        val onAllocationDetailed = extension(DetailedAllocationNotification.serializer(), OnResourceAllocationResult.serializer())
        val onSynchronization = extension(AllocationNotification.serializer(), Unit.serializer())
        val onSynchronizationDetailed = extension(DetailedAllocationNotification.serializer(), Unit.serializer())
    }
}
