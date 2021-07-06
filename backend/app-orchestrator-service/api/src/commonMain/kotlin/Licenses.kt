package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.Serializable

@Serializable
data class LicenseIncludeFlags(
    override val includeOthers: Boolean,
    override val includeUpdates: Boolean,
    override val includeSupport: Boolean,
    override val includeProduct: Boolean,
    override val filterCreatedBy: String?,
    override val filterCreatedAfter: Long?,
    override val filterCreatedBefore: Long?,
    override val filterProvider: String?,
    override val filterProductId: String?,
    override val filterProductCategory: String?
) : ResourceIncludeFlags

@Serializable
data class LicenseSpecification(
    @UCloudApiDoc("The product used for the `License`")
    override val product: ProductReference,
) : ResourceSpecification

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A `License` for use in `Job`s")
@Serializable
data class License(
    override val id: String,

    override val specification: LicenseSpecification,

    @UCloudApiDoc("Information about the owner of this resource")
    override val owner: ResourceOwner,

    @UCloudApiDoc("Information about when this resource was created")
    override val createdAt: Long,

    @UCloudApiDoc("The current status of this resource")
    override val status: LicenseStatus,

    @UCloudApiDoc("A list of updates for this `License`")
    override val updates: List<LicenseUpdate> = emptyList(),

    override val permissions: ResourcePermissions? = null,
) : Resource<Product.License, LicenseSupport> {
    override val billing = ResourceBilling.Free
    override val acl: List<ResourceAclEntry>? = null
}

@UCloudApiDoc("The status of an `License`")
@Serializable
data class LicenseStatus(
    val state: LicenseState,
    override var resolvedSupport: ResolvedSupport<Product.License, LicenseSupport>? = null,
    override var resolvedProduct: Product.License? = null,
    override val boundTo: List<String> = emptyList()
) : JobBoundStatus<Product.License, LicenseSupport>

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@Serializable
enum class LicenseState {
    @UCloudApiDoc(
        "A state indicating that the `License` is currently being prepared and is expected to reach `READY` soon."
    )
    PREPARING,

    @UCloudApiDoc("A state indicating that the `License` is ready for use or already in use.")
    READY,

    @UCloudApiDoc(
        "A state indicating that the `License` is currently unavailable.\n\n" +
                "This state can be used to indicate downtime or service interruptions by the provider."
    )
    UNAVAILABLE
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@Serializable
data class LicenseUpdate(
    @UCloudApiDoc("A timestamp for when this update was registered by UCloud")
    override val timestamp: Long = 0L,

    @UCloudApiDoc("The new state that the `License` transitioned to (if any)")
    override val state: LicenseState? = null,

    @UCloudApiDoc("A new status message for the `License` (if any)")
    override val status: String? = null,

    override val binding: JobBinding? = null,
) : JobBoundUpdate<LicenseState>

@Serializable
data class LicenseSupport(override val product: ProductReference) : ProductSupport

@TSNamespace("compute.licenses")
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object Licenses : ResourceApi<License, LicenseSpecification, LicenseUpdate, LicenseIncludeFlags, LicenseStatus,
        Product.License, LicenseSupport>("licenses") {
    override val typeInfo = ResourceTypeInfo<License, LicenseSpecification, LicenseUpdate,
            LicenseIncludeFlags, LicenseStatus, Product.License, LicenseSupport>()

    override val delete: CallDescription<BulkRequest<FindByStringId>, BulkResponse<Unit?>, CommonErrorMessage>
        get() = super.delete!!
}

@TSNamespace("compute.licenses.control")
object LicenseControl : ResourceControlApi<License, LicenseSpecification, LicenseUpdate, LicenseIncludeFlags,
        LicenseStatus, Product.License, LicenseSupport>("licenses") {
    override val typeInfo =
        ResourceTypeInfo<License, LicenseSpecification, LicenseUpdate, LicenseIncludeFlags, LicenseStatus,
                Product.License, LicenseSupport>()
}

open class LicenseProvider(provider: String) : ResourceProviderApi<License, LicenseSpecification, LicenseUpdate,
        LicenseIncludeFlags, LicenseStatus, Product.License, LicenseSupport>("licenses", provider) {
    override val typeInfo =
        ResourceTypeInfo<License, LicenseSpecification, LicenseUpdate, LicenseIncludeFlags, LicenseStatus,
                Product.License, LicenseSupport>()

    override val delete: CallDescription<BulkRequest<License>, BulkResponse<Unit?>, CommonErrorMessage>
        get() = super.delete!!
}
