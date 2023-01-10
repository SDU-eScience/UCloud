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
        for (notification in notifications) {
            onAllocationTotal.optionalInvoke(this, pluginConfig.extensions.onAllocationTotal, notification)
        }
        return notifications.map { OnResourceAllocationResult.ManageThroughUCloud }
    }

    override suspend fun PluginContext.onResourceAllocationSingle(
        notifications: List<AllocationNotificationSingle>
    ): List<OnResourceAllocationResult> {
        for (notification in notifications) {
            onAllocationSingle.optionalInvoke(this, pluginConfig.extensions.onAllocationSingle, notification)
        }
        return notifications.map { OnResourceAllocationResult.ManageThroughUCloud }
    }

    override suspend fun PluginContext.onResourceSynchronizationTotal(notifications: List<AllocationNotificationTotal>) {
        for (notification in notifications) {
            onSynchronizationTotal.optionalInvoke(this, pluginConfig.extensions.onSynchronizationTotal, notification)
        }
    }

    override suspend fun PluginContext.onResourceSynchronizationSingle(notifications: List<AllocationNotificationSingle>) {
        for (notification in notifications) {
            onSynchronizationSingle.optionalInvoke(this, pluginConfig.extensions.onSynchronizationSingle, notification)
        }
    }

    private companion object Extensions {
        val onAllocationTotal = extension(AllocationNotificationTotal.serializer(), OnResourceAllocationResult.serializer())
        val onAllocationSingle = extension(AllocationNotificationSingle.serializer(), OnResourceAllocationResult.serializer())
        val onSynchronizationTotal = extension(AllocationNotificationTotal.serializer(), Unit.serializer())
        val onSynchronizationSingle = extension(AllocationNotificationSingle.serializer(), Unit.serializer())
    }
}
