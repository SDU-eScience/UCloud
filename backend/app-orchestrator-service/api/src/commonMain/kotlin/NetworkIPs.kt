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
import kotlinx.serialization.Serializable

// Data model
interface NetworkIPId {
    val id: String
}

fun NetworkIPId(id: String): NetworkIPId = NetworkIPRetrieve(id)
@Serializable
data class NetworkIPRetrieve(override val id: String) : NetworkIPId

@Serializable
data class NetworkIPRetrieveWithFlags(
    override val id: String,
    override val includeUpdates: Boolean? = null,
    override val includeProduct: Boolean? = null,
    override val includeAcl: Boolean? = null,
) : NetworkIPDataIncludeFlags, NetworkIPId

interface NetworkIPDataIncludeFlags {
    @UCloudApiDoc("Includes `updates`")
    val includeUpdates: Boolean?

    @UCloudApiDoc("Includes `resolvedProduct`")
    val includeProduct: Boolean?

    @UCloudApiDoc("Includes `acl`")
    val includeAcl: Boolean?
}

@Serializable
data class NetworkIPDataIncludeFlagsImpl(
    override val includeUpdates: Boolean? = null,
    override val includeProduct: Boolean? = null,
    override val includeAcl: Boolean? = null,
) : NetworkIPDataIncludeFlags

fun NetworkIPDataIncludeFlags(
    includeUpdates: Boolean? = null,
    includeProduct: Boolean? = null,
    includeAcl: Boolean? = null,
): NetworkIPDataIncludeFlags = NetworkIPDataIncludeFlagsImpl(includeUpdates, includeProduct, includeAcl)

@Serializable
data class NetworkIPSpecification(
    @UCloudApiDoc("The product used for the `NetworkIP`")
    override val product: ProductReference,
    val firewall: Firewall? = null,
) : ResourceSpecification {
    @Serializable
    data class Firewall(
        val openPorts: List<PortRangeAndProto> = emptyList(),
    )
}

@Serializable
data class FirewallAndId(
    override val id: String,
    val firewall: NetworkIPSpecification.Firewall,
) : NetworkIPId {
    init {
        val ranges = firewall.openPorts.map { it.start..it.end }
        ranges.forEachIndexed { i1, r1 ->
            ranges.forEachIndexed { i2, r2 ->
                if (i1 != i2 && (r2.first in r1 || r2.last in r1 || r1.first in r2 || r1.last in r2)) {
                    throw RPCException(
                        "$r1 and $r2 are overlapping ranges. Only one protocol per range is supported.",
                        HttpStatusCode.BadRequest
                    )
                }
            }
        }

        val numberOfOpenPorts = firewall.openPorts.sumBy { it.end - it.start + 1 }
        if (numberOfOpenPorts > 15_000) {
            // NOTE(Dan): UCloud/compute fails if too many ports are specified (Kubernetes rejects the request)
            throw RPCException(
                "UCloud does not allow for more than 15000 ports to be opened for a single IP address",
                HttpStatusCode.BadRequest
            )
        }

        firewall.openPorts.forEach { port ->
            if (port.start !in MIN_PORT..MAX_PORT || port.end !in MIN_PORT..MAX_PORT) {
                throw RPCException("Invalid port number: ${port.start}..${port.end}", HttpStatusCode.BadRequest)
            }
        }
    }

    companion object {
        private const val MIN_PORT = 0
        private const val MAX_PORT = 65535
    }
}

@Serializable
data class PortRangeAndProto(
    val start: Int,
    val end: Int,
    val protocol: IPProtocol,
)

@Serializable
enum class IPProtocol {
    TCP,
    UDP,
    // No support for any of the others. Some one would have to bring a convincing argument for me to add this.
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A `NetworkIP` for use in `Job`s")
@Serializable
data class NetworkIP(
    override val id: String,

    override val specification: NetworkIPSpecification,

    @UCloudApiDoc("Information about the owner of this resource")
    override val owner: NetworkIPOwner,

    @UCloudApiDoc("Information about when this resource was created")
    override val createdAt: Long,

    @UCloudApiDoc("The current status of this resource")
    override val status: NetworkIPStatus,

    @UCloudApiDoc("Billing information associated with this `NetworkIP`")
    override val billing: NetworkIPBilling,

    @UCloudApiDoc("A list of updates for this `NetworkIP`")
    override val updates: List<NetworkIPUpdate> = emptyList(),

    val resolvedProduct: Product.NetworkIP? = null,

    override val acl: List<ResourceAclEntry<NetworkIPPermission>>? = null,

    override val permissions: ResourcePermissions? = null,
) : Resource<NetworkIPPermission>, NetworkIPId

@Serializable
data class NetworkIPBilling(
    override val pricePerUnit: Long,
    override val creditsCharged: Long,
) : ResourceBilling

@UCloudApiDoc("The status of an `NetworkIP`")
@Serializable
data class NetworkIPStatus(
    val state: NetworkIPState,

    @UCloudApiDoc("The ID of the `Job` that this `NetworkIP` is currently bound to")
    val boundTo: String? = null,

    @UCloudApiDoc("The externally accessible IP address allocated to this `NetworkIP`")
    val ipAddress: String? = null,
) : ResourceStatus

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@Serializable
enum class NetworkIPState {
    @UCloudApiDoc(
        "A state indicating that the `NetworkIP` is currently being prepared and is expected to reach `READY` soon."
    )
    PREPARING,

    @UCloudApiDoc("A state indicating that the `NetworkIP` is ready for use or already in use.")
    READY,

    @UCloudApiDoc(
        "A state indicating that the `NetworkIP` is currently unavailable.\n\n" +
            "This state can be used to indicate downtime or service interruptions by the provider."
    )
    UNAVAILABLE
}

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@Serializable
data class NetworkIPUpdate(
    @UCloudApiDoc("A timestamp for when this update was registered by UCloud")
    override val timestamp: Long,

    @UCloudApiDoc("The new state that the `NetworkIP` transitioned to (if any)")
    val state: NetworkIPState? = null,

    @UCloudApiDoc("A new status message for the `NetworkIP` (if any)")
    override val status: String? = null,

    val didBind: Boolean = false,

    val newBinding: String? = null,

    val changeIpAddress: Boolean? = null,

    val newIpAddress: String? = null,
) : ResourceUpdate

@Serializable
data class NetworkIPOwner(
    @UCloudApiDoc(
        "The username of the user which created this resource.\n\n" +
            "In cases where this user is removed from the project the ownership will be transferred to the current " +
            "PI of the project."
    )
    override val createdBy: String,

    @UCloudApiDoc("The project which owns the resource")
    override val project: String? = null,
) : ResourceOwner

interface NetworkIPFilters {
    val provider: String?
}

// Request and response types
@Serializable
data class NetworkIPsBrowseRequest(
    override val includeUpdates: Boolean? = null,
    override val includeProduct: Boolean? = null,
    override val includeAcl: Boolean? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
    override val provider: String? = null,
) : NetworkIPDataIncludeFlags, NetworkIPFilters, WithPaginationRequestV2

typealias NetworkIPsBrowseResponse = PageV2<NetworkIP>

typealias NetworkIPsCreateRequest = BulkRequest<NetworkIPCreateRequestItem>

typealias NetworkIPCreateRequestItem = NetworkIPSpecification

@Serializable
data class NetworkIPsCreateResponse(val ids: List<String>)

typealias NetworkIPsDeleteRequest = BulkRequest<NetworkIPRetrieve>
typealias NetworkIPsDeleteResponse = Unit

typealias NetworkIPsRetrieveRequest = NetworkIPRetrieveWithFlags
typealias NetworkIPsRetrieveResponse = NetworkIP

@Serializable
enum class NetworkIPPermission {
    USE,
}

typealias NetworkIPsUpdateAclRequest = BulkRequest<NetworkIPsUpdateAclRequestItem>

@Serializable
data class NetworkIPsUpdateAclRequestItem(
    override val id: String,
    val acl: List<ResourceAclEntry<NetworkIPPermission>>,
) : NetworkIPId

typealias NetworkIPsUpdateAclResponse = Unit

typealias NetworkIPsUpdateFirewallRequest = BulkRequest<FirewallAndId>
typealias NetworkIPsUpdateFirewallResponse = Unit

@TSNamespace("compute.networkips")
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object NetworkIPs : CallDescriptionContainer("networkips") {
    const val baseContext = "/api/networkips"

    init {
        title = "Compute: NetworkIPs"
        description = """
            TODO
        """
    }

    val browse = call<NetworkIPsBrowseRequest, NetworkIPsBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }

    val create = call<NetworkIPsCreateRequest, NetworkIPsCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }

    val delete = call<NetworkIPsDeleteRequest, NetworkIPsDeleteResponse, CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }

    val retrieve = call<NetworkIPsRetrieveRequest, NetworkIPsRetrieveResponse, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext)
    }

    val updateAcl = call<NetworkIPsUpdateAclRequest, NetworkIPsUpdateAclResponse, CommonErrorMessage>("updateAcl") {
        httpUpdate(baseContext, "acl")
    }

    val updateFirewall = call<NetworkIPsUpdateFirewallRequest, NetworkIPsUpdateFirewallResponse,
        CommonErrorMessage>("updateFirewall") {
        httpUpdate(baseContext, "firewall")
    }
}
