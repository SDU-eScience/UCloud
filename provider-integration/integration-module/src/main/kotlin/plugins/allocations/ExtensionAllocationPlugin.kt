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

    override suspend fun PluginContext.onWalletUpdated(notifications: List<AllocationPlugin.Message>) {
        for (message in notifications) {
            onWalletUpdated.optionalInvoke(this, pluginConfig.extensions.onWalletUpdated, message)
        }
    }

    private companion object Extensions {
        val onWalletUpdated = extension(AllocationPlugin.Message.serializer(), Unit.serializer())
    }
}
