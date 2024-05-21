package dk.sdu.cloud.plugins

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.ResourceOwnerWithId
import kotlinx.serialization.Serializable


interface AllocationPlugin : Plugin<ConfigSchema.Plugins.Allocations> {
    @Serializable
    data class Message(
        val owner: ResourceOwnerWithId,
        val category: ProductCategory,
        val combinedQuota: Long,
        val locked: Boolean,
        val lastUpdate: Long,
    )

    suspend fun PluginContext.onWalletUpdated(
        notifications: List<Message>
    ) {}
}
