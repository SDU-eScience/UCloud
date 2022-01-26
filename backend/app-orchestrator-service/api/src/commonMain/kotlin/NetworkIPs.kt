package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.ProductPriceUnit
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf

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
) : WithStringId {
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
) : DocVisualizable {
    init {
        if (start < 1 || end < 1) throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
    }

    override fun visualize(): DocVisualization =
        if (start != end) DocVisualization.Inline("$start-$end $protocol")
        else DocVisualization.Inline("$start $protocol")
}

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
    override val owner: ResourceOwner,

    @UCloudApiDoc("Information about when this resource was created")
    override val createdAt: Long,

    @UCloudApiDoc("The current status of this resource")
    override val status: NetworkIPStatus,

    @UCloudApiDoc("A list of updates for this `NetworkIP`")
    override val updates: List<NetworkIPUpdate> = emptyList(),

    val resolvedProduct: Product.NetworkIP? = null,

    override val permissions: ResourcePermissions? = null,
) : Resource<Product.NetworkIP, NetworkIPSupport>

@UCloudApiDoc("The status of an `NetworkIP`")
@Serializable
data class NetworkIPStatus(
    val state: NetworkIPState,

    @UCloudApiDoc("The ID of the `Job` that this `NetworkIP` is currently bound to")
    override val boundTo: List<String> = emptyList(),

    @UCloudApiDoc("The externally accessible IP address allocated to this `NetworkIP`")
    val ipAddress: String? = null,
    override var resolvedSupport: ResolvedSupport<Product.NetworkIP, NetworkIPSupport>? = null,
    override var resolvedProduct: Product.NetworkIP? = null,
) : JobBoundStatus<Product.NetworkIP, NetworkIPSupport>

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
    override val timestamp: Long = 0L,

    @UCloudApiDoc("The new state that the `NetworkIP` transitioned to (if any)")
    override val state: NetworkIPState? = null,

    @UCloudApiDoc("A new status message for the `NetworkIP` (if any)")
    override val status: String? = null,

    val changeIpAddress: Boolean? = null,

    val newIpAddress: String? = null,

    override val binding: JobBinding? = null,
) : JobBoundUpdate<NetworkIPState>

@Serializable
data class NetworkIPFlags(
    val filterState: NetworkIPState? = null,
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
    override val hideProvider: String? = null,
) : ResourceIncludeFlags

@Serializable
data class NetworkIPSupport(
    override val product: ProductReference,
    val firewall: Firewall = Firewall(),
) : ProductSupport {
    @Serializable
    data class Firewall(
        var enabled: Boolean? = null
    )
}

typealias NetworkIPsUpdateFirewallRequest = BulkRequest<FirewallAndId>
typealias NetworkIPsUpdateFirewallResponse = Unit

@Serializable
data class FirewallAndIP(
    val networkIp: NetworkIP,
    val firewall: NetworkIPSpecification.Firewall
)

@TSNamespace("compute.networkips")
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
object NetworkIPs : ResourceApi<NetworkIP, NetworkIPSpecification, NetworkIPUpdate, NetworkIPFlags, NetworkIPStatus,
        Product.NetworkIP, NetworkIPSupport>("networkips") {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        NetworkIP.serializer(),
        typeOf<NetworkIP>(),
        NetworkIPSpecification.serializer(),
        typeOf<NetworkIPSpecification>(),
        NetworkIPUpdate.serializer(),
        typeOf<NetworkIPUpdate>(),
        NetworkIPFlags.serializer(),
        typeOf<NetworkIPFlags>(),
        NetworkIPStatus.serializer(),
        typeOf<NetworkIPStatus>(),
        NetworkIPSupport.serializer(),
        typeOf<NetworkIPSupport>(),
        Product.NetworkIP.serializer(),
        typeOf<Product.NetworkIP>(),
    )

    init {
        val Job = "$TYPE_REF dk.sdu.cloud.app.orchestrator.api.Job"
        description = """
Network IPs grant users access to an IP address resource.

${Resources.readMeFirst}

IPs are used in combination with $Job s. This will attach an IP address to the compute resource. For 
example, on a virtual machine, this might add a new network interface with the IP address.

It is not a strict requirement that the IP address is visible inside the compute environment. However,
it is required that users can access the services exposed by a $Job through this API.

If the firewall feature is supported by the provider, then users must define which ports are expected to be
in use by the $Job . If the firewall feature is not supported, then all ports must be open by default or
managed from within the compute environment. For example, the firewall feature is not supported if the
firewall is controlled by the virtual machine.
        """.trimIndent()
    }

    override fun documentation() {
        useCase(
            "simple",
            "Create and configure firewall",
            flow = {
                val user = basicUser()

                comment("In this example we will see how to create and manage a public IP address")

                success(
                    retrieveProducts,
                    Unit,
                    SupportByProvider(
                        mapOf(
                            "example" to listOf(
                                ResolvedSupport(
                                    Product.NetworkIP(
                                        "example-ip",
                                        1L,
                                        ProductCategoryId("example-id", "example"),
                                        "A public IP address",
                                        unitOfPrice = ProductPriceUnit.PER_UNIT
                                    ),
                                    NetworkIPSupport(
                                        ProductReference("example-ip", "example-ip", "example"),
                                        NetworkIPSupport.Firewall(enabled = true)
                                    )
                                )
                            )
                        )
                    ),
                    user
                )

                comment("We have a single product available to us. It supports the firewall feature.")

                success(
                    create,
                    bulkRequestOf(
                        NetworkIPSpecification(
                            ProductReference("example-ip", "example-ip", "example"),
                            NetworkIPSpecification.Firewall(listOf(PortRangeAndProto(1000, 1100, IPProtocol.TCP)))
                        )
                    ),
                    BulkResponse(listOf(FindByStringId("5123"))),
                    user
                )

                comment("The IP address has been created and has ID 5123")
                comment("Updating the firewall causes existing ports to be removed.")

                success(
                    updateFirewall,
                    bulkRequestOf(
                        FirewallAndId(
                            "5123",
                            NetworkIPSpecification.Firewall(listOf(PortRangeAndProto(80, 80, IPProtocol.TCP)))
                        )
                    ),
                    NetworkIPsUpdateFirewallResponse,
                    user
                )

                comment("We can read the current state by retrieving the resource")

                success(
                    retrieve,
                    ResourceRetrieveRequest(NetworkIPFlags(), "5123"),
                    NetworkIP(
                        "5123",
                        NetworkIPSpecification(
                            ProductReference("example-ip", "example-ip", "example"),
                            NetworkIPSpecification.Firewall(listOf(PortRangeAndProto(80, 80, IPProtocol.TCP)))
                        ),
                        ResourceOwner("user", null),
                        1635170395571L,
                        NetworkIPStatus(NetworkIPState.READY),
                    ),
                    user
                )
            }
        )
    }

    override val create get() = super.create!!
    override val search get() = super.search!!
    override val delete get() = super.delete!!

    val updateFirewall = call<NetworkIPsUpdateFirewallRequest, NetworkIPsUpdateFirewallResponse,
            CommonErrorMessage>("updateFirewall") {
        httpUpdate(baseContext, "firewall")
    }
}

@TSNamespace("compute.networkips.control")
object NetworkIPControl : ResourceControlApi<NetworkIP, NetworkIPSpecification, NetworkIPUpdate, NetworkIPFlags,
        NetworkIPStatus, Product.NetworkIP, NetworkIPSupport>("networkips") {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        NetworkIP.serializer(),
        typeOf<NetworkIP>(),
        NetworkIPSpecification.serializer(),
        typeOf<NetworkIPSpecification>(),
        NetworkIPUpdate.serializer(),
        typeOf<NetworkIPUpdate>(),
        NetworkIPFlags.serializer(),
        typeOf<NetworkIPFlags>(),
        NetworkIPStatus.serializer(),
        typeOf<NetworkIPStatus>(),
        NetworkIPSupport.serializer(),
        typeOf<NetworkIPSupport>(),
        Product.NetworkIP.serializer(),
        typeOf<Product.NetworkIP>(),
    )
}

open class NetworkIPProvider(provider: String) : ResourceProviderApi<NetworkIP, NetworkIPSpecification,
        NetworkIPUpdate, NetworkIPFlags, NetworkIPStatus, Product.NetworkIP, NetworkIPSupport>("networkips", provider) {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        NetworkIP.serializer(),
        typeOf<NetworkIP>(),
        NetworkIPSpecification.serializer(),
        typeOf<NetworkIPSpecification>(),
        NetworkIPUpdate.serializer(),
        typeOf<NetworkIPUpdate>(),
        NetworkIPFlags.serializer(),
        typeOf<NetworkIPFlags>(),
        NetworkIPStatus.serializer(),
        typeOf<NetworkIPStatus>(),
        NetworkIPSupport.serializer(),
        typeOf<NetworkIPSupport>(),
        Product.NetworkIP.serializer(),
        typeOf<Product.NetworkIP>(),
    )

    override val delete get() = super.delete!!

    val updateFirewall = call<BulkRequest<FirewallAndIP>, BulkResponse<Unit?>, CommonErrorMessage>("updateFirewall") {
        httpUpdate(baseContext, "firewall", roles = Roles.PRIVILEGED)
    }
}
