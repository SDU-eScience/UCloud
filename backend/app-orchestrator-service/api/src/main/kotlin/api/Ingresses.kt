package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.PaginationRequestV2Consistency
import dk.sdu.cloud.service.WithPaginationRequestV2

// Data model
interface IngressId {
    val id: String
}

fun IngressId(id: String): IngressId = IngressRetrieve(id)
data class IngressRetrieve(override val id: String) : IngressId

data class IngressRetrieveWithFlags(
    override val id: String,
    override val includeUpdates: Boolean? = null,
    override val includeProduct: Boolean? = null,
) : IngressDataIncludeFlags, IngressId

data class IngressSettings(
    val domainPrefix: String,
    val domainSuffix: String,
)

interface IngressDataIncludeFlags {
    @UCloudApiDoc("Includes `updates`")
    val includeUpdates: Boolean?

    @UCloudApiDoc("Includes `resolvedProduct`")
    val includeProduct: Boolean?
}

data class IngressDataIncludeFlagsImpl(
    override val includeUpdates: Boolean?,
    override val includeProduct: Boolean?
) : IngressDataIncludeFlags

fun IngressDataIncludeFlags(
    includeUpdates: Boolean? = null,
    includeProduct: Boolean? = null,
): IngressDataIncludeFlags = IngressDataIncludeFlagsImpl(includeUpdates, includeProduct)

data class IngressSpecification(
    @UCloudApiDoc("The domain used for L7 load-balancing for use with this `Ingress`")
    val domain: String,

    @UCloudApiDoc("The product used for the `Ingress`")
    override val product: ProductReference
) : ResourceSpecification

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("An L7 ingress-point (HTTP)")
data class Ingress(
    override val id: String,

    override val specification: IngressSpecification,

    @UCloudApiDoc("Information about the owner of this resource")
    override val owner: IngressOwner,

    @UCloudApiDoc("Information about when this resource was created")
    override val createdAt: Long,

    @UCloudApiDoc("The current status of this resource")
    override val status: IngressStatus,

    @UCloudApiDoc("Billing information associated with this `Ingress`")
    override val billing: IngressBilling,

    @UCloudApiDoc("A list of updates for this `Ingress`")
    override val updates: List<IngressUpdate> = emptyList(),

    val resolvedProduct: Product.Ingress? = null,

    override val acl: List<ResourceAclEntry<Nothing?>>? = null
) : IngressId, Resource<Nothing?>

data class IngressBilling(
    override val pricePerUnit: Long,
    override val creditsCharged: Long
) : ResourceBilling

@UCloudApiDoc("The status of an `Ingress`")
data class IngressStatus(
    @UCloudApiDoc("The ID of the `Job` that this `Ingress` is currently bound to")
    val boundTo: String?,

    val state: IngressState
) : ResourceStatus

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
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
data class IngressUpdate(
    @UCloudApiDoc("A timestamp for when this update was registered by UCloud")
    override val timestamp: Long,

    @UCloudApiDoc("The new state that the `Ingress` transitioned to (if any)")
    val state: IngressState? = null,

    @UCloudApiDoc("A new status message for the `Ingress` (if any)")
    override val status: String? = null,

    val didBind: Boolean = false,

    val newBinding: String? = null,
) : ResourceUpdate

data class IngressOwner(
    @UCloudApiDoc(
        "The username of the user which created this resource.\n\n" +
            "In cases where this user is removed from the project the ownership will be transferred to the current " +
            "PI of the project."
    )
    override val createdBy: String,

    @UCloudApiDoc("The project which owns the resource")
    override val project: String? = null
) : ResourceOwner {
    @get:JsonIgnore @Deprecated("Renamed", ReplaceWith("createdBy"))
    val username = createdBy
}

interface IngressFilters {
    val domain: String?
    val provider: String?
}

// Request and response types
data class IngressesBrowseRequest(
    override val includeUpdates: Boolean? = null,
    override val includeProduct: Boolean? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
    override val domain: String? = null,
    override val provider: String? = null,
) : IngressDataIncludeFlags, IngressFilters, WithPaginationRequestV2
typealias IngressesBrowseResponse = PageV2<Ingress>

typealias IngressesCreateRequest = BulkRequest<IngressCreateRequestItem>

typealias IngressCreateRequestItem = IngressSpecification
data class IngressesCreateResponse(val ids: List<String>)

typealias IngressesDeleteRequest = BulkRequest<IngressRetrieve>
typealias IngressesDeleteResponse = Unit

typealias IngressesRetrieveRequest = IngressRetrieveWithFlags
typealias IngressesRetrieveResponse = Ingress

typealias IngressesRetrieveSettingsRequest = ProductReference
typealias IngressesRetrieveSettingsResponse = IngressSettings

@TSNamespace("compute.ingresses")
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object Ingresses : CallDescriptionContainer("ingresses") {
    const val baseContext = "/api/ingresses"

    init {
        title = "Compute: Ingresses"
        description = """
            TODO
        """
    }

    val browse = call<IngressesBrowseRequest, IngressesBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }

    val create = call<IngressesCreateRequest, IngressesCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }

    val delete = call<IngressesDeleteRequest, IngressesDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }

    val retrieve = call<IngressesRetrieveRequest, IngressesRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)
    }

    val retrieveSettings = call<IngressesRetrieveSettingsRequest, IngressesRetrieveSettingsResponse,
        CommonErrorMessage>("retrieveSettings") {
        httpRetrieve(baseContext, "settings")
    }
}
