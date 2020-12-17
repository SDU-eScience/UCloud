package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.*
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

interface IngressSpecification {
    @UCloudApiDoc("The domain used for L7 load-balancing for use with this `Ingress`")
    val domain: String

    @UCloudApiDoc("The product used for the `Ingress`")
    val product: ProductReference
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("An L7 ingress-point (HTTP)")
data class Ingress(
    override val id: String,

    override val domain: String,

    override val product: ProductReference,

    @UCloudApiDoc("Information about the owner of this resource")
    val owner: IngressOwner,

    @UCloudApiDoc("Information about when this resource was created")
    val createdAt: Long,

    @UCloudApiDoc("The current status of this resource")
    val status: IngressStatus,

    @UCloudApiDoc("Billing information associated with this `Ingress`")
    val billing: IngressBilling,

    @UCloudApiDoc("A list of updates for this `Ingress`")
    val updates: List<IngressUpdate> = emptyList(),

    val resolvedProduct: Product.Ingress? = null
) : IngressSpecification, IngressId

data class IngressBilling(
    val pricePerUnit: Long,
    val creditsCharged: Long
)

@UCloudApiDoc("The status of an `Ingress`")
data class IngressStatus(
    @UCloudApiDoc("The ID of the `Job` that this `Ingress` is currently bound to")
    val boundTo: String?,

    val state: IngressState
)

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
    val timestamp: Long,

    @UCloudApiDoc("The new state that the `Ingress` transitioned to (if any)")
    val state: IngressState? = null,

    @UCloudApiDoc("A new status message for the `Ingress` (if any)")
    val status: String? = null,

    val didBind: Boolean = false,

    val newBinding: String? = null,
)

data class IngressOwner(
    @UCloudApiDoc(
        "The username of the user which created this resource.\n\n" +
            "In cases where this user is removed from the project the ownership will be transferred to the current " +
            "PI of the project."
    )
    val username: String,

    @UCloudApiDoc("The project which owns the resource")
    val project: String? = null
)

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

data class IngressCreateRequestItem(
    override val domain: String,
    override val product: ProductReference
) : IngressSpecification
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
