package dk.sdu.cloud.file.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResolvedSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.api.providers.ResourceTypeInfo
import dk.sdu.cloud.provider.api.Provider
import dk.sdu.cloud.provider.api.Resource
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.provider.api.ResourceBilling
import dk.sdu.cloud.provider.api.ResourceIncludeFlags
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.provider.api.ResourcePermissions
import dk.sdu.cloud.provider.api.ResourceSpecification
import dk.sdu.cloud.provider.api.ResourceStatus
import dk.sdu.cloud.provider.api.ResourceUpdate
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.Permission
import kotlinx.serialization.Serializable

@Serializable
data class Share(
    override val id: String,
    override val specification: Spec,
    override val createdAt: Long,
    override val status: Status,
    override val updates: List<Update>,
    override val owner: ResourceOwner,
    override val permissions: ResourcePermissions?
) : Resource<Product.Storage, ShareSupport> {
    override val billing = ResourceBilling.Free
    override val acl: List<ResourceAclEntry>? = null

    @Serializable
    data class Spec(
        val sharedWith: String,
        val sourceFilePath: String,
        val permissions: List<Permission>,
        override val product: ProductReference
    ) : ResourceSpecification

    @Serializable
    data class Update(
        val newState: State,
        val shareAvailableAt: String?,
        override val timestamp: Long,
        override val status: String?
    ) : ResourceUpdate

    @Serializable
    data class Status(
        val shareAvailableAt: String?,
        val state: State,
        override var resolvedSupport: ResolvedSupport<Product.Storage, ShareSupport>? = null,
        override var resolvedProduct: Product.Storage? = null,
    ) : ResourceStatus<Product.Storage, ShareSupport>

    enum class State {
        APPROVED,
        REJECTED,
        PENDING
    }
}

enum class ShareType {
    UCLOUD_MANAGED_COLLECTION
}

@Serializable
data class ShareSupport(
    val type: ShareType,
    override val product: ProductReference
): ProductSupport

@Serializable
data class ShareFlags(
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
) : ResourceIncludeFlags

object Shares : ResourceApi<Share, Share.Spec, Share.Update, ShareFlags, Share.Status,
        Product.Storage, ShareSupport>("shares") {
    override val typeInfo = ResourceTypeInfo<Share, Share.Spec, Share.Update, ShareFlags, Share.Status,
            Product.Storage, ShareSupport>()

    val approve = call<BulkRequest<FindByStringId>, Unit, CommonErrorMessage>("approve") {
        httpUpdate(SharesControl.baseContext, "approve")
    }

    val reject = call<BulkRequest<FindByStringId>, Unit, CommonErrorMessage>("reject") {
        httpUpdate(SharesControl.baseContext, "reject")
    }

    override val create get() = super.create!!
    override val delete get() = super.delete!!
    override val search get() = super.search!!
}

object SharesControl : ResourceControlApi<Share, Share.Spec, Share.Update, ShareFlags, Share.Status,
        Product.Storage, ShareSupport>("shares") {
    override val typeInfo = ResourceTypeInfo<Share, Share.Spec, Share.Update, ShareFlags, Share.Status,
            Product.Storage, ShareSupport>()
}

open class SharesProvider(provider: String) : ResourceProviderApi<Share, Share.Spec, Share.Update, ShareFlags, Share.Status,
        Product.Storage, ShareSupport>("shares", provider) {
    override val typeInfo = ResourceTypeInfo<Share, Share.Spec, Share.Update, ShareFlags, Share.Status,
            Product.Storage, ShareSupport>()

    override val delete get() = super.delete!!
}
