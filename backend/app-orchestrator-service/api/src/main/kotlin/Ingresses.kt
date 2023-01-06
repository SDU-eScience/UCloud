package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.ProductPriceUnit
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.*
import kotlinx.serialization.Serializable

@Serializable
@UCloudApiOwnedBy(Ingresses::class)
@UCloudApiStable
data class IngressSupport(
    val domainPrefix: String,
    val domainSuffix: String,
    override val product: ProductReference,
) : ProductSupport

@Serializable
@UCloudApiOwnedBy(Ingresses::class)
@UCloudApiStable
data class IngressSpecification(
    @UCloudApiDoc("The domain used for L7 load-balancing for use with this `Ingress`")
    val domain: String,

    @UCloudApiDoc("The product used for the `Ingress`")
    override val product: ProductReference
) : ResourceSpecification {
    init {
        checkSingleLine(::domain, domain, maximumSize = 2000)
    }
}

@UCloudApiStable
@UCloudApiDoc("An L7 ingress-point (HTTP)")
@UCloudApiOwnedBy(Ingresses::class)
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
) : Resource<Product.Ingress, IngressSupport>

@UCloudApiDoc("The status of an `Ingress`")
@UCloudApiOwnedBy(Ingresses::class)
@UCloudApiStable
@Serializable
data class IngressStatus(
    @UCloudApiDoc("The ID of the `Job` that this `Ingress` is currently bound to")
    override val boundTo: List<String> = emptyList(),

    val state: IngressState,
    override var resolvedSupport: ResolvedSupport<Product.Ingress, IngressSupport>? = null,
    override var resolvedProduct: Product.Ingress? = null,
) : JobBoundStatus<Product.Ingress, IngressSupport>

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiOwnedBy(Ingresses::class)
@Serializable
@UCloudApiStable
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

@UCloudApiOwnedBy(Ingresses::class)
@Serializable
@UCloudApiStable
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
@UCloudApiStable
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
    override val filterProviderIds: String? = null,
    override val filterIds: String? = null,
    val filterState: IngressState? = null,
    override val hideProductId: String? = null,
    override val hideProductCategory: String? = null,
    override val hideProvider: String? = null,
) : ResourceIncludeFlags

@TSNamespace("compute.ingresses")
@UCloudApiStable
object Ingresses : ResourceApi<
    Ingress,
    IngressSpecification,
    IngressUpdate,
    IngressIncludeFlags,
    IngressStatus,
    Product.Ingress,
    IngressSupport>("ingresses") {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        Ingress.serializer(),
        typeOfIfPossible<Ingress>(),
        IngressSpecification.serializer(),
        typeOfIfPossible<IngressSpecification>(),
        IngressUpdate.serializer(),
        typeOfIfPossible<IngressUpdate>(),
        IngressIncludeFlags.serializer(),
        typeOfIfPossible<IngressIncludeFlags>(),
        IngressStatus.serializer(),
        typeOfIfPossible<IngressStatus>(),
        IngressSupport.serializer(),
        typeOfIfPossible<IngressSupport>(),
        Product.Ingress.serializer(),
        typeOfIfPossible<Product.Ingress>(),
    )

    init {
        val Job = "$TYPE_REF dk.sdu.cloud.app.orchestrator.api.Job"
        description = """
Ingresses provide a way to attach custom links to interactive web-interfaces.

${Resources.readMeFirst}

When an interactive (web) application runs, it typically uses a provider generated URL. The ingress feature
allows providers to give access to these $Job s through a custom URL.
        """.trimIndent()
    }

    override fun documentation() {
        useCase(
            "simple",
            "Create and configure an Ingress",
            flow = {
                val user = basicUser()

                comment("In this example, we will see how to create and manage an ingress")

                success(
                    retrieveProducts,
                    Unit,
                    SupportByProvider(
                        mapOf(
                            "example" to listOf(
                                ResolvedSupport(
                                    Product.Ingress(
                                        "example-ingress",
                                        1L,
                                        ProductCategoryId("example-ingress", "example-ingress"),
                                        "An example ingress",
                                        unitOfPrice = ProductPriceUnit.PER_UNIT
                                    ),
                                    IngressSupport(
                                        "app-",
                                        ".example.com",
                                        ProductReference("example-ingress", "example-ingress", "example")
                                    ),
                                )
                            )
                        )
                    ),
                    user
                )

                comment("""
                    We have a single product available. This product requires that all ingresses start with "app-" and 
                    ends with ".example.com"
                """.trimIndent())

                comment("""
                    📝 NOTE: Providers can perform additional validation. For example, must providers won't support 
                    arbitrary levels of sub-domains. That is, must providers would reject the value 
                    app-this.is.not.what.we.want.example.com.
                """.trimIndent())

                val spec = IngressSpecification(
                    "app-mylink.example.com",
                    ProductReference("example-ingress", "example-ingress", "example")
                )

                success(
                    create,
                    bulkRequestOf(spec),
                    BulkResponse(listOf(FindByStringId("5127"))),
                    user
                )

                success(
                    retrieve,
                    ResourceRetrieveRequest(IngressIncludeFlags(), "5127"),
                    Ingress(
                        "5127",
                        spec,
                        ResourceOwner("user", null),
                        1635170395571L,
                        IngressStatus(state = IngressState.READY)
                    ),
                    user
                )
            }
        )
    }

    override val create get() = super.create!!
    override val delete get() = super.delete!!
    override val search get() = super.search!!
}

@TSNamespace("compute.ingresses.control")
@UCloudApiStable
object IngressControl : ResourceControlApi<Ingress, IngressSpecification, IngressUpdate, IngressIncludeFlags,
        IngressStatus, Product.Ingress, IngressSupport>("ingresses") {

    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        Ingress.serializer(),
        typeOfIfPossible<Ingress>(),
        IngressSpecification.serializer(),
        typeOfIfPossible<IngressSpecification>(),
        IngressUpdate.serializer(),
        typeOfIfPossible<IngressUpdate>(),
        IngressIncludeFlags.serializer(),
        typeOfIfPossible<IngressIncludeFlags>(),
        IngressStatus.serializer(),
        typeOfIfPossible<IngressStatus>(),
        IngressSupport.serializer(),
        typeOfIfPossible<IngressSupport>(),
        Product.Ingress.serializer(),
        typeOfIfPossible<Product.Ingress>(),
    )
}

@UCloudApiStable
open class IngressProvider(provider: String) : ResourceProviderApi<Ingress, IngressSpecification, IngressUpdate,
        IngressIncludeFlags, IngressStatus, Product.Ingress, IngressSupport>("ingresses", provider) {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        Ingress.serializer(),
        typeOfIfPossible<Ingress>(),
        IngressSpecification.serializer(),
        typeOfIfPossible<IngressSpecification>(),
        IngressUpdate.serializer(),
        typeOfIfPossible<IngressUpdate>(),
        IngressIncludeFlags.serializer(),
        typeOfIfPossible<IngressIncludeFlags>(),
        IngressStatus.serializer(),
        typeOfIfPossible<IngressStatus>(),
        IngressSupport.serializer(),
        typeOfIfPossible<IngressSupport>(),
        Product.Ingress.serializer(),
        typeOfIfPossible<Product.Ingress>(),
    )

    override val delete: CallDescription<BulkRequest<Ingress>, BulkResponse<Unit?>, CommonErrorMessage>
        get() = super.delete!!
}
