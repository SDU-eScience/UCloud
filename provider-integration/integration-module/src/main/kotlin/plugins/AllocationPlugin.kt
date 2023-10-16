package dk.sdu.cloud.plugins

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.ResourceOwnerWithId
import kotlinx.serialization.*

@Serializable
sealed class AllocationNotification {
    abstract var quota: Long
    abstract var owner: ResourceOwnerWithId
    abstract val productCategory: String
    abstract val productType: ProductType

    @Serializable
    @SerialName("Combined")
    data class Combined(
        override var quota: Long,
        override var owner: ResourceOwnerWithId,
        override val productCategory: String,
        override val productType: ProductType,
    ) : AllocationNotification()

    @Serializable
    @SerialName("Single")
    data class Single(
        override var quota: Long,
        override var owner: ResourceOwnerWithId,
        override val productCategory: String,
        override val productType: ProductType,
        val allocationId: String,
    ) : AllocationNotification()
}

interface AllocationPlugin : Plugin<ConfigSchema.Plugins.Allocations> {
    suspend fun PluginContext.onResourceAllocationTotal(
        notifications: List<AllocationNotification.Combined>
    ) {

    }

    suspend fun PluginContext.onResourceAllocationSingle(
        notifications: List<AllocationNotification.Single>
    ) {
    }

    suspend fun PluginContext.onResourceSynchronizationTotal(
        notifications: List<AllocationNotification.Combined>
    ) {
        // Do nothing
    }

    suspend fun PluginContext.onResourceSynchronizationSingle(
        notifications: List<AllocationNotification.Single>
    ) {
        // Do nothing
    }

    suspend fun PluginContext.notify(notification: List<AllocationNotification>, isSynchronization: Boolean) {
        if (notification.isEmpty()) return

        if (isSynchronization) {
            when (notification.first()) {
                is AllocationNotification.Combined -> onResourceAllocationTotal(notification.filterIsInstance<AllocationNotification.Combined>())
                is AllocationNotification.Single -> onResourceAllocationSingle(notification.filterIsInstance<AllocationNotification.Single>())
            }
        } else {
            when (notification.first()) {
                is AllocationNotification.Combined -> onResourceSynchronizationTotal(notification.filterIsInstance<AllocationNotification.Combined>())
                is AllocationNotification.Single -> onResourceSynchronizationSingle(notification.filterIsInstance<AllocationNotification.Single>())
            }
        }
    }
}
