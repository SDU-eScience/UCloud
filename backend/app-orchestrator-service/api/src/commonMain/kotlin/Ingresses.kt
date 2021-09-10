package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class IngressSupport(
    val domainPrefix: String,
    val domainSuffix: String,
    override val product: ProductReference,
) : ProductSupport

@Serializable
data class IngressSpecification(
    @UCloudApiDoc("The domain used for L7 load-balancing for use with this `Ingress`")
    val domain: String,

    @UCloudApiDoc("The product used for the `Ingress`")
    override val product: ProductReference
) : ResourceSpecification {
    init {
        if (domain.length > 2000) {
            throw RPCException("domain size cannot exceed 2000 characters", HttpStatusCode.BadRequest)
        }
    }
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("An L7 ingress-point (HTTP)")
@Serializable
data class Ingress(
    override val id: String,

    override val specification: IngressSpecification,

    @UCloudApiDoc("Information about the owner of this resource")
    override val owner: ResourceOwner,

    @UCloudApiDoc("Information about when this resource was created")
    override val createdAt: Long,

    @UCloudApiDoc("The current status of this resource")
    override val status: IngressStatus,

    @UCloudApiDoc("A list of updates for this `Ingress`")
    override val updates: List<IngressUpdate> = emptyList(),

    override val permissions: ResourcePermissions? = null,
) : Resource<Product.Ingress, IngressSupport> {
    override val billing = ResourceBilling.Free
    override val acl: List<ResourceAclEntry>? = null
}

@UCloudApiDoc("The status of an `Ingress`")
@Serializable
data class IngressStatus(
    @UCloudApiDoc("The ID of the `Job` that this `Ingress` is currently bound to")
    override val boundTo: List<String> = emptyList(),

    val state: IngressState,
    override var resolvedSupport: ResolvedSupport<Product.Ingress, IngressSupport>? = null,
    override var resolvedProduct: Product.Ingress? = null,
) : JobBoundStatus<Product.Ingress, IngressSupport>

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@Serializable
enum class IngressState {
    @UCloudApiDoc(
        "A state indicating that the `Ingress` is currently being prepared and is expected to reach `READY` soon."
    )
    PREPARING,

    @UCloudApiDoc("A state indicating that the `Ingress` is ready for use or already in use.")
    READY,

    @UCloudApiDoc(
        "A state indicating that the `Ingress` is currently unavailable.\n\n" +
            "This state can be used to indicate downtime or service interruptions by the provider."
    )
    UNAVAILABLE
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@Serializable
data class IngressUpdate(
    @UCloudApiDoc("The new state that the `Ingress` transitioned to (if any)")
    override val state: IngressState? = null,

    @UCloudApiDoc("A new status message for the `Ingress` (if any)")
    override val status: String? = null,

    @UCloudApiDoc("A timestamp for when this update was registered by UCloud")
    override val timestamp: Long = 0,

    override val binding: JobBinding? = null,
) : JobBoundUpdate<IngressState>

@Serializable
data class IngressIncludeFlags(
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
    val filterState: IngressState? = null,
) : ResourceIncludeFlags

@TSNamespace("compute.ingresses")
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object Ingresses : ResourceApi<
    Ingress,
    IngressSpecification,
    IngressUpdate,
    IngressIncludeFlags,
    IngressStatus,
    Product.Ingress,
    IngressSupport>("ingresses") {
    override val typeInfo = ResourceTypeInfo<Ingress, IngressSpecification, IngressUpdate,
        IngressIncludeFlags, IngressStatus, Product.Ingress, IngressSupport>()

    override val create get() = super.create!!
    override val delete get() = super.delete!!
    override val search get() = super.search!!
}

@TSNamespace("compute.ingresses.control")
object IngressControl : ResourceControlApi<Ingress, IngressSpecification, IngressUpdate, IngressIncludeFlags,
        IngressStatus, Product.Ingress, IngressSupport>("ingresses") {

    override val typeInfo =
        ResourceTypeInfo<Ingress, IngressSpecification, IngressUpdate, IngressIncludeFlags, IngressStatus,
                Product.Ingress, IngressSupport>()
}

open class IngressProvider(provider: String) : ResourceProviderApi<Ingress, IngressSpecification, IngressUpdate,
        IngressIncludeFlags, IngressStatus, Product.Ingress, IngressSupport>("ingresses", provider) {
    override val typeInfo =
        ResourceTypeInfo<Ingress, IngressSpecification, IngressUpdate, IngressIncludeFlags, IngressStatus,
                Product.Ingress, IngressSupport>()

    override val delete: CallDescription<BulkRequest<Ingress>, BulkResponse<Unit?>, CommonErrorMessage>
        get() = super.delete!!
}
