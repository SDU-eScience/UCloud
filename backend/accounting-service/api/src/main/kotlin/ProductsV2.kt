package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.provider.api.translateToChargeType
import dk.sdu.cloud.provider.api.translateToProductPriceUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
@UCloudApiOwnedBy(ProductsV2::class)
@UCloudApiDoc(
    """
        Products define the services exposed by a Provider.
        
        For more information see [this](/docs/developer-guide/accounting-and-projects/products.md) page.
    """
)
@UCloudApiStable
sealed class ProductV2 {
    @UCloudApiDoc("The category groups similar products together, it also defines which provider owns the product")
    abstract val category: ProductCategory

    @UCloudApiDoc("A unique name associated with this Product")
    abstract val name: String

    @UCloudApiDoc("A short (single-line) description of the Product")
    abstract val description: String

    @UCloudApiDoc("Classifier used to explain the type of Product")
    abstract val productType: ProductType

    @UCloudApiDoc("Price is for usage of a single product in the accountingFrequency period specified by the product category.")
    abstract val price: Long

    @UCloudApiDoc(
        """
        Flag to indicate that this Product is not publicly available
        
        ⚠️ WARNING: This doesn't make the $TYPE_REF Product secret. In only hides the $TYPE_REF Product from the grant
        system's UI.
    """
    )
    abstract val hiddenInGrantApplications: Boolean

    @UCloudApiDoc("Included only with certain endpoints which support `includeBalance`")
    var usage: Long? = null

    protected fun verify() {
        checkSingleLine(::name, name)
        checkSingleLine(::description, description)
    }

    abstract fun toV1(): Product

    @Serializable
    @SerialName("storage")
    @UCloudApiStable
    data class Storage(
        override val name: String,
        override val price: Long,
        override val category: ProductCategory,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
    ) : ProductV2() {
        override val productType: ProductType = ProductType.STORAGE

        init {
            verify()
        }

        override fun toV1(): Product = Product.Storage(
            name,
            price,
            ProductCategoryId(category.name, category.provider),
            description,
            chargeType = translateToChargeType(category),
            unitOfPrice = translateToProductPriceUnit(category.productType, category.name),
            hiddenInGrantApplications = hiddenInGrantApplications
        )

        override fun toString() = super.toString()
    }

    @Serializable
    @SerialName("compute")
    @UCloudApiStable
    data class Compute(
        override val name: String,
        override val price: Long,
        override val category: ProductCategory,
        override val description: String = "",
        val cpu: Int? = null,
        val memoryInGigs: Int? = null,
        val gpu: Int? = null,
        val cpuModel: String? = null,
        val memoryModel: String? = null,
        val gpuModel: String? = null,
        override val hiddenInGrantApplications: Boolean = false,
    ) : ProductV2() {
        override val productType: ProductType = ProductType.COMPUTE

        init {
            verify()

            if (gpu != null) checkMinimumValue(::gpu, gpu, 0)
            if (cpu != null) checkMinimumValue(::cpu, cpu, 0)
            if (memoryInGigs != null) checkMinimumValue(::memoryInGigs, memoryInGigs, 0)
            if (cpuModel != null) checkSingleLine(::cpuModel, cpuModel, maximumSize = 128)
            if (gpuModel != null) checkSingleLine(::gpuModel, gpuModel, maximumSize = 128)
            if (memoryModel != null) checkSingleLine(::memoryModel, memoryModel, maximumSize = 128)
        }

        override fun toV1(): Product = Product.Compute(
            name,
            price,
            ProductCategoryId(category.name, category.provider),
            description,
            chargeType = translateToChargeType(category),
            unitOfPrice = translateToProductPriceUnit(category.productType, category.name),
            hiddenInGrantApplications = hiddenInGrantApplications,
            cpu = cpu,
            gpu = gpu,
            memoryInGigs = memoryInGigs,
            cpuModel = cpuModel,
            gpuModel = gpuModel,
            memoryModel = memoryModel
        )

        override fun toString() = super.toString()
    }

    @Serializable
    @SerialName("ingress")
    @UCloudApiStable
    data class Ingress(
        override val name: String,
        override val price: Long,
        override val category: ProductCategory,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
    ) : ProductV2() {
        override val productType: ProductType = ProductType.INGRESS

        init {
            verify()
        }

        override fun toV1(): Product = Product.Ingress(
            name,
            price,
            ProductCategoryId(category.name, category.provider),
            description,
            chargeType = translateToChargeType(category),
            unitOfPrice = translateToProductPriceUnit(category.productType, category.name),
            hiddenInGrantApplications = hiddenInGrantApplications
        )

        override fun toString() = super.toString()
    }

    @Serializable
    @SerialName("license")
    @UCloudApiStable
    data class License(
        override val name: String,
        override val price: Long,
        override val category: ProductCategory,
        override val description: String = "",
        val tags: List<String> = emptyList(),
        override val hiddenInGrantApplications: Boolean = false,
    ) : ProductV2() {
        override val productType: ProductType = ProductType.LICENSE

        init {
            verify()
        }

        override fun toV1(): Product = Product.License(
            name,
            price,
            ProductCategoryId(category.name, category.provider),
            description,
            tags = tags,
            chargeType = translateToChargeType(category),
            unitOfPrice = translateToProductPriceUnit(category.productType, category.name),
            hiddenInGrantApplications = hiddenInGrantApplications
        )

        override fun toString() = super.toString()
    }

    @Serializable
    @SerialName("network_ip")
    @UCloudApiStable
    data class NetworkIP(
        override val name: String,
        override val price: Long,
        override val category: ProductCategory,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
    ) : ProductV2() {
        override val productType: ProductType = ProductType.NETWORK_IP

        init {
            verify()
        }

        override fun toV1(): Product = Product.NetworkIP(
            name,
            price,
            ProductCategoryId(category.name, category.provider),
            description,
            chargeType = translateToChargeType(category),
            unitOfPrice = translateToProductPriceUnit(category.productType, category.name),
            hiddenInGrantApplications = hiddenInGrantApplications
        )

        override fun toString() = super.toString()
    }

    override fun toString(): String {
        return "${name}/${category.name}@${category.provider}"
    }
}

@Suppress("DEPRECATION")
typealias ProductReferenceV2 = ProductReference

interface ProductFlagsV2 {
    val filterName: String?
    val filterProductType: ProductType?
    val filterProvider: String?
    val filterCategory: String?
    val filterUsable: Boolean?
    val includeBalance: Boolean?
    val includeMaxBalance: Boolean?
}

@Serializable
data class ProductsV2BrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,

    override val filterName: String? = null,
    override val filterProvider: String? = null,
    override val filterProductType: ProductType? = null,
    override val filterCategory: String? = null,
    override val filterUsable: Boolean? = null,

    override val includeBalance: Boolean? = null,
    override val includeMaxBalance: Boolean? = null
) : WithPaginationRequestV2, ProductFlagsV2
typealias ProductsV2BrowseResponse = PageV2<ProductV2>

@Serializable
data class ProductsV2RetrieveRequest(
    override val filterName: String,
    override val filterCategory: String,
    override val filterProvider: String,

    override val filterProductType: ProductType? = null,
    override val filterUsable: Boolean? = null,

    override val includeBalance: Boolean? = null,
    override val includeMaxBalance: Boolean? = null,
) : ProductFlagsV2

object ProductsV2 : CallDescriptionContainer("products.v2") {
    const val baseContext = "/api/products/v2"

    private const val browseUseCase = "browse"
    private const val browseByTypeUseCase = "browse-by-type"
    private const val retrieveUseCase = "retrieve"

    init {
        serializerLookupTable = mapOf(
            serializerEntry(ProductV2.serializer())
        )

        val Provider = "$TYPE_REF dk.sdu.cloud.provider.api.Provider"
        description = """
Products define the services exposed by a Provider.

$Provider s expose services into UCloud. But, different 
$Provider s expose different services. UCloud uses $TYPE_REF ProductV2 s to define the 
services of a $Provider . As an example, a 
$Provider might have the following services:

- __Storage:__ Two tiers of storage. Fast storage, for short-lived data. Slower storage, for long-term data storage.
- __Compute:__ Three tiers of compute. Slim nodes for ordinary computations. Fat nodes for memory-hungry applications. 
  GPU powered nodes for artificial intelligence.

For many $Provider s, the story doesn't stop here. You can often allocate your 
$TYPE_REF dk.sdu.cloud.app.orchestrator.api.Job s on a machine "slice". This can increase overall utilization, as users 
aren't forced to request full nodes. A $Provider might advertise the following
slices:

| Name | vCPU | RAM (GB) | GPU | Price |
|------|------|----------|-----|-------|
| `example-slim-1` | 1 | 4 | 0 | 0,100 DKK/hr |
| `example-slim-2` | 2 | 8 | 0 | 0,200 DKK/hr |
| `example-slim-4` | 4 | 16 | 0 | 0,400 DKK/hr |
| `example-slim-8` | 8 | 32 | 0 | 0,800 DKK/hr |

__Table:__ A single node-type split up into individual slices.

## Concepts

UCloud represent these concepts in the following abstractions:

- $TYPE_REF ProductType: A classifier for a $TYPE_REF ProductV2, defines the behavior of a $TYPE_REF ProductV2 .
- $TYPE_REF ProductCategory: A group of similar $TYPE_REF Product s. In most cases, $TYPE_REF Product s in a category
  run on identical hardware. 
- $TYPE_REF ProductV2: Defines a concrete service exposed by a $Provider.

Below, we show an example of how a $Provider can organize their services.

![](/backend/accounting-service/wiki/products.png)

__Figure:__ All $TYPE_REF ProductV2 s in UCloud are of a specific type, such as: `STORAGE` and `COMPUTE`.
$Provider s have zero or more categories of every type, e.g. `example-slim`. 
In a given category, the $Provider has one or more slices.

## Payment Model

UCloud uses a flexible payment model, which allows $Provider s to use a model which
is familiar to them. The payment model is defined in the $TYPE_REF ProductCategory. Here the provider will define the
$TYPE_REF AccountingUnit and $TYPE_REF AccountingFrequency. The unit describes the unit in which the provider reports
usage. This unit is opaque to UCloud/Core and UCloud/Core will not attempt to convert or in any way interpret the
meaning of this unit. The frequency describes how often a charge occurs. These are either periodic or non-periodic.
UCloud's accounting system has slightly different rules depending on if a product is periodic or non-periodic. Providers
are not required to report once for have period. The frequency simply tells UCloud/Core how to interpret the unit. For
example a combination of `unit = Core` and `frequency = PERIODIC_MINUTE` should be interpreted as core-minutes. UCloud's
frontend may choose to convert this into core-hours for better readability, but the internal numbers are stored in
minutes.

        """.trimIndent()
    }

    val create = call(
        "create",
        BulkRequest.serializer(ProductV2.serializer()),
        Unit.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpCreate(baseContext, roles = setOf(Role.SERVICE, Role.ADMIN, Role.PROVIDER))

        documentation {
            summary = "Creates a new $TYPE_REF Product in UCloud"
            description = """
                Only providers and UCloud administrators can create a $TYPE_REF Product . When this endpoint is
                invoked by a provider, then the provider field of the $TYPE_REF Product must match the invoking user.
                
                The $TYPE_REF Product will become ready and visible in UCloud immediately after invoking this call.
                If no $TYPE_REF Product has been created in this category before, then this category will be created.
                
                ---
                
                __📝 NOTE:__ Most properties of a $TYPE_REF ProductCategory are immutable and must not be changed.
                As a result, you cannot create a new $TYPE_REF Product later with different category properties.
                
                ---
                
                If the $TYPE_REF Product already exists, then the existing product is overwritten.
            """.trimIndent()
        }
    }

    val retrieve = call(
        "retrieve",
        ProductsV2RetrieveRequest.serializer(),
        ProductV2.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpRetrieve(baseContext, roles = Roles.AUTHENTICATED)

        documentation {
            summary = "Retrieve a single product"
            useCaseReference(retrieveUseCase, "Retrieving a single product by ID")
        }
    }

    val browse = call(
        "browse",
        {
            httpBrowse(baseContext, roles = Roles.PUBLIC)

            documentation {
                summary = "Browse a set of products"
                description = "This endpoint uses the normal pagination and filter mechanisms to return a list " +
                        "of $TYPE_REF ProductV2 ."
                useCaseReference(browseUseCase, "Browse in the full product catalog")
                useCaseReference(browseByTypeUseCase, "Browse for a specific type of product (e.g. compute)")
            }
        },
        ProductsV2BrowseRequest.serializer(),
        PageV2.serializer(ProductV2.serializer()),
        CommonErrorMessage.serializer(),
        typeOfIfPossible<ProductsV2BrowseRequest>(),
        typeOfIfPossible<ProductsV2BrowseResponse>(),
        typeOfIfPossible<CommonErrorMessage>()
    )
}
