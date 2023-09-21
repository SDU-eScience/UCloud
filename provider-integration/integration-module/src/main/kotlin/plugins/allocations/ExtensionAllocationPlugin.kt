package dk.sdu.cloud.plugins.allocations

import dk.sdu.cloud.config.*
import dk.sdu.cloud.plugins.*
import kotlinx.serialization.builtins.serializer

class ExtensionAllocationPlugin : AllocationPlugin {
    override val pluginTitle: String = "Extension"
    private lateinit var pluginConfig: ConfigSchema.Plugins.Allocations.Extension

    override fun configure(config: ConfigSchema.Plugins.Allocations) {
        this.pluginConfig = config as ConfigSchema.Plugins.Allocations.Extension
    }

    override suspend fun PluginContext.onResourceAllocationTotal(
        notifications: List<AllocationNotification.Combined>
    ) {
        for (notification in notifications) {
            onAllocationTotal.optionalInvoke(this, pluginConfig.extensions.onAllocationTotal, notification)
        }
    }

    override suspend fun PluginContext.onResourceAllocationSingle(
        notifications: List<AllocationNotification.Single>
    ) {
        for (notification in notifications) {
            onAllocationSingle.optionalInvoke(this, pluginConfig.extensions.onAllocationSingle, notification)
        }
    }

    override suspend fun PluginContext.onResourceSynchronizationTotal(notifications: List<AllocationNotification.Combined>) {
        for (notification in notifications) {
            onSynchronizationTotal.optionalInvoke(this, pluginConfig.extensions.onSynchronizationTotal, notification)
        }
    }

    override suspend fun PluginContext.onResourceSynchronizationSingle(notifications: List<AllocationNotification.Single>) {
        for (notification in notifications) {
            onSynchronizationSingle.optionalInvoke(this, pluginConfig.extensions.onSynchronizationSingle, notification)
        }
    }

    private companion object Extensions {
        val onAllocationTotal = extension(AllocationNotification.Combined.serializer(), Unit.serializer())
        val onAllocationSingle = extension(AllocationNotification.Single.serializer(), Unit.serializer())
        val onSynchronizationTotal = extension(AllocationNotification.Combined.serializer(), Unit.serializer())
        val onSynchronizationSingle = extension(AllocationNotification.Single.serializer(), Unit.serializer())
    }
}
