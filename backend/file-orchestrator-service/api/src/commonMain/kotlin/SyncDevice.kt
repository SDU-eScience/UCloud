package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf

@Serializable
@UCloudApiInternal(InternalLevel.BETA)
data class SyncDevice(
    override val id: String,
    override val specification: Spec,
    override val createdAt: Long,
    override val status: Status,
    override val updates: List<Update>,
    override val owner: ResourceOwner,
    override val permissions: ResourcePermissions?
) : Resource<Product.Synchronization, SyncDeviceSupport> {

    @Serializable
    @UCloudApiInternal(InternalLevel.BETA)
    data class Spec(
        val deviceId: String,
        override val product: ProductReference,
    ) : ResourceSpecification

    @Serializable
    @UCloudApiInternal(InternalLevel.BETA)
    data class Status(
        override var resolvedSupport: ResolvedSupport<Product.Synchronization, SyncDeviceSupport>? = null,
        override var resolvedProduct: Product.Synchronization? = null
    ) : ResourceStatus<Product.Synchronization, SyncDeviceSupport>

    @Serializable
    @UCloudApiInternal(InternalLevel.BETA)
    data class Update(
        override val timestamp: Long,
        override val status: String?
    ) : ResourceUpdate
}

typealias SyncDeviceSupport = SyncFolderSupport

@Serializable
@UCloudApiInternal(InternalLevel.BETA)
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
    override val filterProviderIds: String? = null,
    override val filterIds: String? = null,
    override val hideProductId: String? = null,
    override val hideProductCategory: String? = null,
    override val hideProvider: String? = null
) : ResourceIncludeFlags

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiInternal(InternalLevel.BETA)
object SyncDevices : ResourceApi<SyncDevice, SyncDevice.Spec, SyncDevice.Update, SyncDeviceIncludeFlags, SyncDevice.Status,
    Product.Synchronization, SyncDeviceSupport>("sync.devices") {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        SyncDevice.serializer(),
        typeOf<SyncDevice>(),
        SyncDevice.Spec.serializer(),
        typeOf<SyncDevice.Spec>(),
        SyncDevice.Update.serializer(),
        typeOf<SyncDevice.Update>(),
        SyncDeviceIncludeFlags.serializer(),
        typeOf<SyncDeviceIncludeFlags>(),
        SyncDevice.Status.serializer(),
        typeOf<SyncDevice.Status>(),
        SyncDeviceSupport.serializer(),
        typeOf<SyncDeviceSupport>(),
        Product.Synchronization.serializer(),
        typeOf<Product.Synchronization>()
    )

    override val create get() = super.create!!
    override val delete get() = super.delete!!
    override val search get() = super.search!!
}

@UCloudApiInternal(InternalLevel.BETA)
object SyncDeviceControl : ResourceControlApi<SyncDevice, SyncDevice.Spec, SyncDevice.Update, SyncDeviceIncludeFlags,
    SyncDevice.Status, Product.Synchronization, SyncDeviceSupport>("sync.devices") {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        SyncDevice.serializer(),
        typeOf<SyncDevice>(),
        SyncDevice.Spec.serializer(),
        typeOf<SyncDevice.Spec>(),
        SyncDevice.Update.serializer(),
        typeOf<SyncDevice.Update>(),
        SyncDeviceIncludeFlags.serializer(),
        typeOf<SyncDeviceIncludeFlags>(),
        SyncDevice.Status.serializer(),
        typeOf<SyncDevice.Status>(),
        SyncDeviceSupport.serializer(),
        typeOf<SyncDeviceSupport>(),
        Product.Synchronization.serializer(),
        typeOf<Product.Synchronization>()
    )
}

@UCloudApiInternal(InternalLevel.BETA)
open class SyncDeviceProvider(provider: String) : ResourceProviderApi<SyncDevice, SyncDevice.Spec, SyncDevice.Update,
    SyncDeviceIncludeFlags, SyncDevice.Status, Product.Synchronization, SyncDeviceSupport>("sync.devices", provider) {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        SyncDevice.serializer(),
        typeOf<SyncDevice>(),
        SyncDevice.Spec.serializer(),
        typeOf<SyncDevice.Spec>(),
        SyncDevice.Update.serializer(),
        typeOf<SyncDevice.Update>(),
        SyncDeviceIncludeFlags.serializer(),
        typeOf<SyncDeviceIncludeFlags>(),
        SyncDevice.Status.serializer(),
        typeOf<SyncDevice.Status>(),
        SyncDeviceSupport.serializer(),
        typeOf<SyncDeviceSupport>(),
        Product.Synchronization.serializer(),
        typeOf<Product.Synchronization>()
    )

    override val delete get() = super.delete!!
}
