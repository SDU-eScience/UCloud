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

    override suspend fun PluginContext.onResourceAllocationTotal(
        notifications: List<AllocationNotificationTotal>
    ): List<OnResourceAllocationResult> {
        val results = ArrayList<OnResourceAllocationResult>()
        for (notification in notifications) {
            results.add(onAllocationTotal.invoke(pluginConfig.extensions.onAllocationTotal, notification))
        }
        return results
    }

    override suspend fun PluginContext.onResourceAllocationSingle(
        notifications: List<AllocationNotificationSingle>
    ): List<OnResourceAllocationResult> {
        val results = ArrayList<OnResourceAllocationResult>()
        for (notification in notifications) {
            results.add(onAllocationSingle.invoke(pluginConfig.extensions.onAllocationSingle, notification))
        }
        return results
    }

    override suspend fun PluginContext.onResourceSynchronizationTotal(notifications: List<AllocationNotificationTotal>) {
        for (notification in notifications) {
            onSynchronizationTotal.invoke(pluginConfig.extensions.onSynchronizationTotal, notification)
        }
    }

    override suspend fun PluginContext.onResourceSynchronizationSingle(notifications: List<AllocationNotificationSingle>) {
        for (notification in notifications) {
            onSynchronizationSingle.invoke(pluginConfig.extensions.onSynchronizationSingle, notification)
        }
    }

    private companion object Extensions {
        val onAllocationTotal = extension(AllocationNotificationTotal.serializer(), OnResourceAllocationResult.serializer())
        val onAllocationSingle = extension(AllocationNotificationSingle.serializer(), OnResourceAllocationResult.serializer())
        val onSynchronizationTotal = extension(AllocationNotificationTotal.serializer(), Unit.serializer())
        val onSynchronizationSingle = extension(AllocationNotificationSingle.serializer(), Unit.serializer())
    }
}
