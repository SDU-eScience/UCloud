package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf

@Serializable
enum class SynchronizationType(val syncthingValue: String) {
    SEND_RECEIVE("sendreceive"),
    SEND_ONLY("sendonly")
}

@Serializable
data class SyncFolder(
    override val id: String,
    override val specification: Spec,
    override val createdAt: Long,
    override val status: Status,
    override val updates: List<Update>,
    override val owner: ResourceOwner,
    override val permissions: ResourcePermissions?,
) : Resource<Product.Synchronization, SyncFolderSupport> {

    @Serializable
    data class Spec(
        val path: String,
        override val product: ProductReference,
    ) : ResourceSpecification

    @Serializable
    data class Status(
        val deviceId: String? = null,
        val syncType: SynchronizationType? = null,
        override var resolvedSupport: ResolvedSupport<Product.Synchronization, SyncFolderSupport>? = null,
        override var resolvedProduct: Product.Synchronization? = null
    ) : ResourceStatus<Product.Synchronization, SyncFolderSupport>

    @Serializable
    data class Update(
        override val timestamp: Long,
        override val status: String?,
        val deviceId: String? = null,
        val syncType: SynchronizationType? = null,
    ) : ResourceUpdate
}

@Serializable
data class SyncFolderSupport(override val product: ProductReference) : ProductSupport

@Serializable
data class SyncFolderIncludeFlags(
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
    override val hideProvider: String? = null,
    override val hideProductCategory: String? = null,
    override val hideProductId: String? = null,
    val filterByPath: String? = null,
    val filterDeviceId: List<String>? = null,
) : ResourceIncludeFlags

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object SyncFolders : ResourceApi<SyncFolder, SyncFolder.Spec, SyncFolder.Update, SyncFolderIncludeFlags, SyncFolder.Status,
    Product.Synchronization, SyncFolderSupport>("sync.folders") {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        SyncFolder.serializer(),
        typeOf<SyncFolder>(),
        SyncFolder.Spec.serializer(),
        typeOf<SyncFolder.Spec>(),
        SyncFolder.Update.serializer(),
        typeOf<SyncFolder.Update>(),
        SyncFolderIncludeFlags.serializer(),
        typeOf<SyncFolderIncludeFlags>(),
        SyncFolder.Status.serializer(),
        typeOf<SyncFolder.Status>(),
        SyncFolderSupport.serializer(),
        typeOf<SyncFolderSupport>(),
        Product.Synchronization.serializer(),
        typeOf<Product.Synchronization>()
    )

    override val create get() = super.create!!
    override val delete get() = super.delete!!
    override val search get() = super.search!!
}

object SyncFolderControl : ResourceControlApi<SyncFolder, SyncFolder.Spec, SyncFolder.Update, SyncFolderIncludeFlags,
    SyncFolder.Status, Product.Synchronization, SyncFolderSupport>("sync.folders") {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        SyncFolder.serializer(),
        typeOf<SyncFolder>(),
        SyncFolder.Spec.serializer(),
        typeOf<SyncFolder.Spec>(),
        SyncFolder.Update.serializer(),
        typeOf<SyncFolder.Update>(),
        SyncFolderIncludeFlags.serializer(),
        typeOf<SyncFolderIncludeFlags>(),
        SyncFolder.Status.serializer(),
        typeOf<SyncFolder.Status>(),
        SyncFolderSupport.serializer(),
        typeOf<SyncFolderSupport>(),
        Product.Synchronization.serializer(),
        typeOf<Product.Synchronization>()
    )
}

open class SyncFolderProvider(provider: String) : ResourceProviderApi<SyncFolder, SyncFolder.Spec, SyncFolder.Update,
    SyncFolderIncludeFlags, SyncFolder.Status, Product.Synchronization, SyncFolderSupport>("sync.folders", provider) {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        SyncFolder.serializer(),
        typeOf<SyncFolder>(),
        SyncFolder.Spec.serializer(),
        typeOf<SyncFolder.Spec>(),
        SyncFolder.Update.serializer(),
        typeOf<SyncFolder.Update>(),
        SyncFolderIncludeFlags.serializer(),
        typeOf<SyncFolderIncludeFlags>(),
        SyncFolder.Status.serializer(),
        typeOf<SyncFolder.Status>(),
        SyncFolderSupport.serializer(),
        typeOf<SyncFolderSupport>(),
        Product.Synchronization.serializer(),
        typeOf<Product.Synchronization>()
    )

    override val delete get() = super.delete!!

    val onFilePermissionsUpdated = call<BulkRequest<SyncFolder>, BulkResponse<Unit?>, CommonErrorMessage>("onFilePermissionsUpdated") {
        httpUpdate(baseContext, "filePermissionsUpdated", Roles.PROVIDER)
    }
}
