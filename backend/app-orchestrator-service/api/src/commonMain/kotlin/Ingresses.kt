package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import io.ktor.http.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// Data model
interface IngressId {
    val id: String
}

fun IngressId(id: String): IngressId = IngressRetrieve(id)
@Serializable
data class IngressRetrieve(override val id: String) : IngressId

@Serializable
data class IngressRetrieveWithFlags(
    override val id: String,
    override val includeUpdates: Boolean? = null,
    override val includeProduct: Boolean? = null,
) : IngressDataIncludeFlags, IngressId

@Serializable
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

@Serializable
data class IngressDataIncludeFlagsImpl(
    override val includeUpdates: Boolean? = null,
    override val includeProduct: Boolean? = null,
) : IngressDataIncludeFlags

fun IngressDataIncludeFlags(
    includeUpdates: Boolean? = null,
    includeProduct: Boolean? = null,
): IngressDataIncludeFlags = IngressDataIncludeFlagsImpl(includeUpdates, includeProduct)

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

    @UCloudApiDoc("Billing information associated with this `Ingress`")
    override val billing: IngressBilling,

    @UCloudApiDoc("A list of updates for this `Ingress`")
    override val updates: List<IngressUpdate> = emptyList(),

    val resolvedProduct: Product.Ingress? = null,

    override val acl: List<ResourceAclEntry<@Contextual Nothing?>>? = null,

    override val permissions: ResourcePermissions? = null,
) : IngressId, Resource<Nothing?>

@Serializable
data class IngressBilling(
    override val pricePerUnit: Long,
    override val creditsCharged: Long
) : ResourceBilling

@UCloudApiDoc("The status of an `Ingress`")
@Serializable
data class IngressStatus(
    @UCloudApiDoc("The ID of the `Job` that this `Ingress` is currently bound to")
    val boundTo: String? = null,

    val state: IngressState
) : ResourceStatus

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
    @UCloudApiDoc("A timestamp for when this update was registered by UCloud")
    override val timestamp: Long,

    @UCloudApiDoc("The new state that the `Ingress` transitioned to (if any)")
    val state: IngressState? = null,

    @UCloudApiDoc("A new status message for the `Ingress` (if any)")
    override val status: String? = null,

    val didBind: Boolean = false,

    val newBinding: String? = null,
) : ResourceUpdate

interface IngressFilters {
    val domain: String?
    val provider: String?
}

// Request and response types
@Serializable
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
@Serializable
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
