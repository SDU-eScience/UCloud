package dk.sdu.cloud.app.orchestrator.api

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
@UCloudApiStable
data class LicenseIncludeFlags(
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
@UCloudApiStable
data class LicenseSpecification(
    @UCloudApiDoc("The product used for the `License`")
    override val product: ProductReference,
) : ResourceSpecification

@UCloudApiStable
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
) : Resource<Product.License, LicenseSupport>

@UCloudApiDoc("The status of an `License`")
@Serializable
@UCloudApiStable
data class LicenseStatus(
    val state: LicenseState,
    override var resolvedSupport: ResolvedSupport<Product.License, LicenseSupport>? = null,
    override var resolvedProduct: Product.License? = null,
    override val boundTo: List<String> = emptyList()
) : JobBoundStatus<Product.License, LicenseSupport>

@UCloudApiStable
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

@UCloudApiStable
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
@UCloudApiStable
data class LicenseSupport(override val product: ProductReference) : ProductSupport

@TSNamespace("compute.licenses")
@UCloudApiStable
object Licenses : ResourceApi<License, LicenseSpecification, LicenseUpdate, LicenseIncludeFlags, LicenseStatus,
        Product.License, LicenseSupport>("licenses") {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        License.serializer(),
        typeOfIfPossible<License>(),
        LicenseSpecification.serializer(),
        typeOfIfPossible<LicenseSpecification>(),
        LicenseUpdate.serializer(),
        typeOfIfPossible<LicenseUpdate>(),
        LicenseIncludeFlags.serializer(),
        typeOfIfPossible<LicenseIncludeFlags>(),
        LicenseStatus.serializer(),
        typeOfIfPossible<LicenseStatus>(),
        LicenseSupport.serializer(),
        typeOfIfPossible<LicenseSupport>(),
        Product.License.serializer(),
        typeOfIfPossible<Product.License>(),
    )

    init {
        val Application = "$TYPE_REF dk.sdu.cloud.app.store.api.Application"
        val Job = "$TYPE_REF dk.sdu.cloud.app.orchestrator.api.Job"
        description = """
Licenses act as a key to certain restricted software.

${Resources.readMeFirst}

Users must attach a license to a $Job for it to work. When attached, the software should be available. 
In most cases, a license is a parameter of an $Application .

---

__📝 NOTE:__ UCloud does not store any information about license keys, servers or any other credentials. It is 
the responsibility of the provider to store this information.

---
        """.trimIndent()
    }

    override fun documentation() {
        useCase(
            "simple",
            "Create and configure a license",
            flow = {
                val user = basicUser()
                comment("In this example we will see how to create and manage a software license")

                success(
                    retrieveProducts,
                    Unit,
                    SupportByProvider(
                        mapOf(
                            "example" to listOf(
                                ResolvedSupport(
                                    Product.License(
                                        "example-license",
                                        1L,
                                        ProductCategoryId("example-license", "example"),
                                        "An example license",
                                        unitOfPrice = ProductPriceUnit.PER_UNIT
                                    ),
                                    LicenseSupport(
                                        ProductReference("example-license", "example-license", "example")
                                    )
                                )
                            )
                        )
                    ),
                    user
                )

                success(
                    create,
                    bulkRequestOf(
                        LicenseSpecification(
                            ProductReference("example-license", "example-license", "example"),
                        )
                    ),
                    BulkResponse(listOf(FindByStringId("5123"))),
                    user
                )

                success(
                    retrieve,
                    ResourceRetrieveRequest(LicenseIncludeFlags(), "5123"),
                    License(
                        "5123",
                        LicenseSpecification(
                            ProductReference("example-license", "example-license", "example"),
                        ),
                        ResourceOwner("user", null),
                        1635170395571L,
                        LicenseStatus(LicenseState.READY)
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

@TSNamespace("compute.licenses.control")
@UCloudApiStable
object LicenseControl : ResourceControlApi<License, LicenseSpecification, LicenseUpdate, LicenseIncludeFlags,
        LicenseStatus, Product.License, LicenseSupport>("licenses") {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        License.serializer(),
        typeOfIfPossible<License>(),
        LicenseSpecification.serializer(),
        typeOfIfPossible<LicenseSpecification>(),
        LicenseUpdate.serializer(),
        typeOfIfPossible<LicenseUpdate>(),
        LicenseIncludeFlags.serializer(),
        typeOfIfPossible<LicenseIncludeFlags>(),
        LicenseStatus.serializer(),
        typeOfIfPossible<LicenseStatus>(),
        LicenseSupport.serializer(),
        typeOfIfPossible<LicenseSupport>(),
        Product.License.serializer(),
        typeOfIfPossible<Product.License>(),
    )
}

@UCloudApiStable
open class LicenseProvider(provider: String) : ResourceProviderApi<License, LicenseSpecification, LicenseUpdate,
        LicenseIncludeFlags, LicenseStatus, Product.License, LicenseSupport>("licenses", provider) {
    @OptIn(ExperimentalStdlibApi::class)
    override val typeInfo = ResourceTypeInfo(
        License.serializer(),
        typeOfIfPossible<License>(),
        LicenseSpecification.serializer(),
        typeOfIfPossible<LicenseSpecification>(),
        LicenseUpdate.serializer(),
        typeOfIfPossible<LicenseUpdate>(),
        LicenseIncludeFlags.serializer(),
        typeOfIfPossible<LicenseIncludeFlags>(),
        LicenseStatus.serializer(),
        typeOfIfPossible<LicenseStatus>(),
        LicenseSupport.serializer(),
        typeOfIfPossible<LicenseSupport>(),
        Product.License.serializer(),
        typeOfIfPossible<Product.License>(),
    )

    override val delete get() = super.delete!!
}
