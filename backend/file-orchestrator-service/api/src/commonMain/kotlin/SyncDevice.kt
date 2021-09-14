package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.ExperimentalLevel
import dk.sdu.cloud.calls.TSNamespace
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.Serializable

@Serializable
data class SyncDevice(
    override val id: String,
    override val specification: Spec,
    override val createdAt: Long,
    override val status: Status,
    override val updates: List<Update>,
    override val owner: ResourceOwner,
    override val permissions: ResourcePermissions?
) : Resource<Product.Synchronization, SyncDeviceSupport> {
    override val billing = ResourceBilling.Free
    override val acl: List<ResourceAclEntry>? = null

    @Serializable
    data class Spec(
        val deviceId: String,
        override val product: ProductReference,
    ) : ResourceSpecification

    @Serializable
    data class Status(
        override var resolvedSupport: ResolvedSupport<Product.Synchronization, SyncDeviceSupport>? = null,
        override var resolvedProduct: Product.Synchronization? = null
    ) : ResourceStatus<Product.Synchronization, SyncDeviceSupport>

    @Serializable
    data class Update(
        override val timestamp: Long,
        override val status: String?
    ) : ResourceUpdate
}

typealias SyncDeviceSupport = SyncFolderSupport

@Serializable
data class SyncDeviceIncludeFlags(
    override val includeOthers: Boolean = false,
    override val includeUpdates: Boolean = false,
    override val includeSupport: Boolean = false,
    override val includeProduct: Boolean = false,
    override val filterCreatedBy: String? = null,
    override val filterCreatedAfter: Long? = null,
    override val filterCreatedBefore: Long? = null,
    override val filterProvider: String? = null,
    override val filterProductId: String? = null,
    override val filterProductCategory: String? = null,
    override val filterProviderId: String? = null,
    val filterOwner: List<String>? = null
) : ResourceIncludeFlags

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object SyncDevices : ResourceApi<SyncDevice, SyncDevice.Spec, SyncDevice.Update, SyncDeviceIncludeFlags, SyncDevice.Status,
    Product.Synchronization, SyncDeviceSupport>("sync.devices") {
    override val typeInfo = ResourceTypeInfo<SyncDevice, SyncDevice.Spec, SyncDevice.Update, SyncDeviceIncludeFlags,
        SyncDevice.Status, Product.Synchronization, SyncDeviceSupport>()

    override val create get() = super.create!!
    override val delete get() = super.delete!!
    override val search get() = super.search!!
}

object SyncDeviceControl : ResourceControlApi<SyncDevice, SyncDevice.Spec, SyncDevice.Update, SyncDeviceIncludeFlags,
    SyncDevice.Status, Product.Synchronization, SyncDeviceSupport>("sync.devices") {
    override val typeInfo =
        ResourceTypeInfo<SyncDevice, SyncDevice.Spec, SyncDevice.Update, SyncDeviceIncludeFlags, SyncDevice.Status,
            Product.Synchronization, SyncDeviceSupport>()
}

open class SyncDeviceProvider(provider: String) : ResourceProviderApi<SyncDevice, SyncDevice.Spec, SyncDevice.Update,
    SyncDeviceIncludeFlags, SyncDevice.Status, Product.Synchronization, SyncDeviceSupport>("sync.devices", provider) {
    override val typeInfo =
        ResourceTypeInfo<SyncDevice, SyncDevice.Spec, SyncDevice.Update, SyncDeviceIncludeFlags, SyncDevice.Status,
            Product.Synchronization, SyncDeviceSupport>()

    override val delete get() = super.delete!!
}
