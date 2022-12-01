package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Deprecated("Replace with ProductType", ReplaceWith("ProductType"))
typealias ProductArea = ProductType

@Serializable
@UCloudApiOwnedBy(Products::class)
@UCloudApiDoc(
    """
    A classifier for a $TYPE_REF Product
    
    For more information, see the individual $TYPE_REF Product s:
    
    - `STORAGE`: See $TYPE_REF Product.Storage
    - `COMPUTE`: See $TYPE_REF Product.Compute
    - `INGRESS`: See $TYPE_REF Product.Ingress
    - `LICENSE`: See $TYPE_REF Product.License
    - `NETWORK_IP`: See $TYPE_REF Product.NetworkIP
"""
)
enum class ProductType {
    @UCloudApiDoc("See Product.Storage")
    STORAGE,

    @UCloudApiDoc("See Product.Compute")
    COMPUTE,

    @UCloudApiDoc("See Product.Ingress")
    INGRESS,

    @UCloudApiDoc("See Product.License")
    LICENSE,

    @UCloudApiDoc("See Product.NetworkIP")
    NETWORK_IP,
}

@Serializable
@UCloudApiOwnedBy(Products::class)
enum class ChargeType {
    ABSOLUTE,
    DIFFERENTIAL_QUOTA
}

@UCloudApiOwnedBy(Products::class)
enum class ProductPriceUnit {
    CREDITS_PER_UNIT,
    PER_UNIT,

    CREDITS_PER_MINUTE,
    CREDITS_PER_HOUR,
    CREDITS_PER_DAY,

    UNITS_PER_MINUTE,
    UNITS_PER_HOUR,
    UNITS_PER_DAY,
}

@Serializable
@UCloudApiOwnedBy(Products::class)
data class ProductCategoryId(
    val name: String,
    val provider: String
) : DocVisualizable {
    @Deprecated("Renamed to name", ReplaceWith("name"))
    val id: String
        get() = name

    override fun visualize(): DocVisualization {
        return DocVisualization.Inline("$name / $provider")
    }
}

@Serializable
@UCloudApiOwnedBy(Products::class)
@UCloudApiDoc("Contains a unique reference to a [Product](/backend/accounting-service/README.md)")
data class ProductReference(
    @UCloudApiDoc("The `Product` ID")
    val id: String,
    @UCloudApiDoc("The ID of the `Product`'s category")
    val category: String,
    @UCloudApiDoc("The provider of the `Product`")
    val provider: String,
) : DocVisualizable {
    override fun visualize(): DocVisualization = DocVisualization.Inline("$id / $category / $provider")
}

@Serializable
@UCloudApiOwnedBy(Products::class)
@UCloudApiDoc(
    """
    Products define the services exposed by a Provider.
    
    For more information see [this](/docs/developer-guide/accounting-and-projects/products.md) page.
"""
)
sealed class Product : DocVisualizable {
    @UCloudApiDoc("The category groups similar products together, it also defines which provider owns the product")
    abstract val category: ProductCategoryId

    @UCloudApiDoc(
        """
        The price of a single unit in a single period
        
        For more information go 
        [here](/docs/developer-guide/accounting-and-projects/products.md#understanding-the-price).
    """
    )
    abstract val pricePerUnit: Long

    @UCloudApiDoc("A unique name associated with this Product")
    abstract val name: String

    @UCloudApiDoc("A short (single-line) description of the Product")
    abstract val description: String

    @UCloudApiDoc("A integer used for changing the order in which products are displayed (ascending order)")
    abstract val priority: Int

    @UCloudApiDoc("A version number for this Product, managed by UCloud")
    @Deprecated("No longer used")
    abstract val version: Int

    @UCloudApiDoc(
        """
        Indicates that a Wallet is not required to use this Product
        
        Under normal circumstances, a $TYPE_REF Wallet is always required. This is required even if a $TYPE_REF Product
        has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.
    """
    )
    abstract val freeToUse: Boolean

    @UCloudApiDoc("Classifier used to explain the type of Product")
    abstract val productType: ProductType

    @UCloudApiDoc("The unit of price. Used in combination with chargeType to create a complete payment model.")
    abstract val unitOfPrice: ProductPriceUnit

    @UCloudApiDoc(
        "The category of payment model. Used in combination with unitOfPrice to create a complete payment " +
            "model."
    )
    abstract val chargeType: ChargeType

    @UCloudApiDoc(
        """
        Flag to indicate that this Product is not publicly available
        
        ⚠️ WARNING: This doesn't make the $TYPE_REF Product secret. In only hides the $TYPE_REF Product from the grant
        system's UI.
    """
    )
    abstract val hiddenInGrantApplications: Boolean

    @Deprecated("Replace with name", ReplaceWith("name"))
    val id: String
        get() = name

    @UCloudApiDoc("Included only with certain endpoints which support `includeBalance`")
    var balance: Long? = null

    @OptIn(ExperimentalStdlibApi::class)
    override fun visualize(): DocVisualization {
        return DocVisualization.Card(
            "$name / ${category.name} / ${category.provider} (Product.${this::class.simpleName ?: ""})",
            buildList {
                add(DocStatLine.of("" to DocVisualization.Inline(description)))
                addAll(visualizePaymentModel(pricePerUnit, chargeType, unitOfPrice))
            },
            emptyList(),
        )
    }

    protected fun verify() {
        checkMinimumValue(::pricePerUnit, pricePerUnit, 0)
        checkSingleLine(::name, name)
        checkSingleLine(::description, description)

        when (unitOfPrice) {
            ProductPriceUnit.UNITS_PER_MINUTE,
            ProductPriceUnit.UNITS_PER_HOUR,
            ProductPriceUnit.UNITS_PER_DAY -> {
                if (chargeType != ChargeType.ABSOLUTE) {
                    throw RPCException("UNITS_PER_X cannot be used with DIFFERENTIAL_QUOTA", HttpStatusCode.BadRequest)
                }
            }

            ProductPriceUnit.CREDITS_PER_UNIT,
            ProductPriceUnit.CREDITS_PER_MINUTE,
            ProductPriceUnit.CREDITS_PER_HOUR,
            ProductPriceUnit.CREDITS_PER_DAY -> {
                if (chargeType != ChargeType.ABSOLUTE) {
                    throw RPCException(
                        "CREDITS_PER_X cannot be used with DIFFERENTIAL_QUOTA",
                        HttpStatusCode.BadRequest
                    )
                }
            }

            ProductPriceUnit.PER_UNIT -> {
                // OK
            }
        }
    }

    @Serializable
    @SerialName("storage")
    @UCloudApiDoc(
        """
        A storage Product
        
        | Unit | API |
        |------|-----|
        | Measured in GB (10⁹ bytes. 1 byte = 1 octet) | [Click here](/docs/developer-guide/orchestration/storage/files.md) |
    """
    )
    data class Storage(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val priority: Int = 0,
        override val version: Int = 1,
        override val freeToUse: Boolean = false,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.CREDITS_PER_DAY,
        override val chargeType: ChargeType = ChargeType.ABSOLUTE,
        override val hiddenInGrantApplications: Boolean = false,
    ) : Product() {
        override val productType: ProductType = ProductType.STORAGE

        init {
            verify()
        }

        override fun toString() = super.toString()
    }

    @Serializable
    @SerialName("compute")
    @UCloudApiDoc(
        """
        A compute Product
        
        | Unit | API |
        |------|-----|
        | Measured in hyper-threaded cores (vCPU) | [Click here](/docs/developer-guide/orchestration/compute/jobs.md) |
    """
    )
    data class Compute(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val priority: Int = 0,
        val cpu: Int? = null,
        val memoryInGigs: Int? = null,
        val gpu: Int? = null,
        val cpuModel: String? = null,
        val memoryModel: String? = null,
        val gpuModel: String? = null,
        override val version: Int = 1,
        override val freeToUse: Boolean = false,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.CREDITS_PER_MINUTE,
        override val chargeType: ChargeType = ChargeType.ABSOLUTE,
        override val hiddenInGrantApplications: Boolean = false,
    ) : Product() {
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

        @OptIn(ExperimentalStdlibApi::class)
        override fun visualize(): DocVisualization {
            val baseVisualization = super.visualize() as DocVisualization.Card
            return baseVisualization.copy(
                lines = baseVisualization.lines + listOf(
                    DocStatLine.of("" to DocVisualization.Inline(buildList {
                        if (cpu != null) add("$cpu vCPU")
                        if (memoryInGigs != null) add("$memoryInGigs GB RAM")
                        if (gpu != null) add("$gpu GPU")
                    }.joinToString(", ")))
                )
            )
        }

        override fun toString() = super.toString()
    }

    @Serializable
    @SerialName("ingress")
    @UCloudApiDoc(
        """
        An ingress Product
        
        | Unit | API |
        |------|-----|
        | Measured in number of ingresses | [Click here](/docs/developer-guide/orchestration/compute/ingress.md) |
    """
    )
    data class Ingress(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val priority: Int = 0,
        override val version: Int = 1,
        override val freeToUse: Boolean = false,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.PER_UNIT,
        override val chargeType: ChargeType = ChargeType.ABSOLUTE,
        override val hiddenInGrantApplications: Boolean = false,
    ) : Product() {
        override val productType: ProductType = ProductType.INGRESS

        init {
            verify()
        }

        override fun toString() = super.toString()
    }

    @Serializable
    @SerialName("license")
    @UCloudApiDoc(
        """
        A license Product
        
        | Unit | API |
        |------|-----|
        | Measured in number of licenses | [Click here](/docs/developer-guide/orchestration/compute/license.md) |
    """
    )
    data class License(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val priority: Int = 0,
        val tags: List<String> = emptyList(),
        override val version: Int = 1,
        override val freeToUse: Boolean = false,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.PER_UNIT,
        override val chargeType: ChargeType = ChargeType.ABSOLUTE,
        override val hiddenInGrantApplications: Boolean = false,
    ) : Product() {
        override val productType: ProductType = ProductType.LICENSE

        init {
            verify()
        }

        override fun toString() = super.toString()
    }

    @Serializable
    @SerialName("network_ip")
    @UCloudApiDoc(
        """
        An IP address Product
        
        | Unit | API |
        |------|-----|
        | Measured in number of IP addresses | [Click here](/docs/developer-guide/orchestration/compute/ips.md) |
    """
    )
    data class NetworkIP(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val priority: Int = 0,
        override val version: Int = 1,
        override val freeToUse: Boolean = false,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.PER_UNIT,
        override val chargeType: ChargeType = ChargeType.ABSOLUTE,
        override val hiddenInGrantApplications: Boolean = false,
    ) : Product() {
        override val productType: ProductType = ProductType.NETWORK_IP

        init {
            verify()
        }

        override fun toString() = super.toString()
    }

    companion object {
        @OptIn(ExperimentalStdlibApi::class)
        fun visualizePaymentModel(
            pricePerUnit: Long?,
            chargeType: ChargeType,
            unitOfPrice: ProductPriceUnit
        ): List<DocStatLine> {
            return buildList {
                when {
                    chargeType == ChargeType.DIFFERENTIAL_QUOTA -> {
                        add(DocStatLine.of("Payment model" to DocVisualization.Inline("Differential (Quota)")))
                    }
                    unitOfPrice == ProductPriceUnit.PER_UNIT -> {
                        add(DocStatLine.of("Payment model" to DocVisualization.Inline("One-time payment (units)")))
                    }
                    unitOfPrice == ProductPriceUnit.CREDITS_PER_UNIT -> {
                        val explanation = if (pricePerUnit == null) {
                            "(DKK)"
                        } else {
                            "(${(pricePerUnit / 1_000_000.0)} DKK)"
                        }

                        add(
                            DocStatLine.of(
                                "Payment model" to DocVisualization.Inline(
                                    "One-time payment " + explanation
                                )
                            )
                        )
                    }
                    unitOfPrice == ProductPriceUnit.UNITS_PER_MINUTE ||
                        unitOfPrice == ProductPriceUnit.UNITS_PER_HOUR ||
                        unitOfPrice == ProductPriceUnit.UNITS_PER_DAY -> {
                        add(
                            DocStatLine.of(
                                "Payment model" to DocVisualization.Inline(
                                    "Periodic (per " +
                                        "${unitOfPrice.name.substringAfterLast('_').lowercase()})"
                                )
                            )
                        )
                    }
                    unitOfPrice == ProductPriceUnit.CREDITS_PER_MINUTE ||
                        unitOfPrice == ProductPriceUnit.CREDITS_PER_HOUR ||
                        unitOfPrice == ProductPriceUnit.CREDITS_PER_DAY -> {
                        val explanation = if (pricePerUnit == null) {
                            "(DKK)"
                        } else {
                            "(${pricePerUnit / 1_000_000.0}DKK/" +
                                unitOfPrice.name.substringAfterLast('_').lowercase() + ")"
                        }
                        add(
                            DocStatLine.of(
                                "Payment model" to DocVisualization.Inline("Periodic $explanation")
                            )
                        )
                    }
                }

            }
        }
    }

    override fun toString(): String {
        return "${name}/${category.name}@${category.provider}"
    }
}

interface ProductFlags {
    val filterName: String?
    val filterArea: ProductType?
    val filterProvider: String?
    val filterCategory: String?
    val filterVersion: Int?
    val includeBalance: Boolean?
}

@Serializable
data class ProductsBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,

    override val filterName: String? = null,
    override val filterProvider: String? = null,
    override val filterArea: ProductType? = null,
    override val filterCategory: String? = null,
    override val filterVersion: Int? = null,

    val showAllVersions: Boolean? = null,

    override val includeBalance: Boolean? = null
) : WithPaginationRequestV2, ProductFlags
typealias ProductsBrowseResponse = PageV2<Product>

@Serializable
data class ProductsRetrieveRequest(
    override val filterName: String,
    override val filterCategory: String,
    override val filterProvider: String,

    override val filterArea: ProductType? = null,
    override val filterVersion: Int? = null,

    override val includeBalance: Boolean? = null,
) : ProductFlags

@OptIn(ExperimentalStdlibApi::class)
object Products : CallDescriptionContainer("products") {
    const val baseContext = "/api/products"

    init {
        serializerLookupTable = mapOf(
            serializerEntry(Product.serializer())
        )

        val Provider = "$TYPE_REF dk.sdu.cloud.provider.api.Provider"
        description = """
Products define the services exposed by a Provider.

$Provider s expose services into UCloud. But, different 
$Provider s expose different services. UCloud uses $TYPE_REF Product s to define the 
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

- $TYPE_REF ProductType: A classifier for a $TYPE_REF Product, defines the behavior of a $TYPE_REF Product .
- $TYPE_REF ProductCategory: A group of similar $TYPE_REF Product s. In most cases, $TYPE_REF Product s in a category
  run on identical hardware. 
- $TYPE_REF Product: Defines a concrete service exposed by a $Provider.

Below, we show an example of how a $Provider can organize their services.

![](/backend/accounting-service/wiki/products.png)

__Figure:__ All $TYPE_REF Product s in UCloud are of a specific type, such as: `STORAGE` and `COMPUTE`.
$Provider s have zero or more categories of every type, e.g. `example-slim`. 
In a given category, the $Provider has one or more slices.

## Payment Model

UCloud uses a flexible payment model, which allows $Provider s to use a model which
is familiar to them. In short, a $Provider must first choose one of the following payment types:

1. __Differential models__ ([`ChargeType.DIFFERENTIAL_QUOTA`]($TYPE_REF_LINK ChargeType))
   1. Quota ([`ProductPriceUnit.PER_UNIT`]($TYPE_REF_LINK ProductPriceUnit))
2. __Absolute models__ ([`ChargeType.ABSOLUTE`]($TYPE_REF_LINK ChargeType))
   1. One-time payment ([`ProductPriceUnit.PER_UNIT`]($TYPE_REF_LINK ProductPriceUnit) and 
      [`ProductPriceUnit.CREDITS_PER_UNIT`]($TYPE_REF_LINK ProductPriceUnit))
   2. Periodic payment ([`ProductPriceUnit.UNITS_PER_X`]($TYPE_REF_LINK ProductPriceUnit) and 
      [`ProductPriceUnit.CREDITS_PER_X`]($TYPE_REF_LINK ProductPriceUnit))
      
---

__📝 NOTE:__ To select a model, you must specify a $TYPE_REF ChargeType and a $TYPE_REF ProductPriceUnit . We have 
shown all valid combinations above.  

---

Quotas put a strict limit on the "number of units" in concurrent use. UCloud measures this number in a 
$TYPE_REF Product specific way. A unit is pre-defined and stable across the entirety of UCloud. A few quick 
examples:

- __Storage:__ Measured in GB (10⁹ bytes. 1 byte = 1 octet)
- __Compute:__ Measured in hyper-threaded cores (vCPU)
- __Public IPs:__ Measured in IP addresses
- __Public links:__ Measured in public links

If using an absolute model, then you must choose the unit of allocation:

- You specify allocations in units (`UNITS_PER_X`). For example: 3000 IP addresses.
- You specify allocations in money (`CREDITS_PER_X`). For example: 1000 DKK.

---

__📝 NOTE:__ For precision purposes, UCloud specifies all money sums as integers. As a result, 1 UCloud credit is equal 
to one millionth of a Danish Crown (DKK).

---

When using periodic payments, you must also specify the length of a single period. $Provider s are not required to 
report usage once for every period. But the reporting itself must specify usage in number of periods. 
UCloud supports period lengths of a minute, one hour and one day.

## Understanding the price

All $TYPE_REF Product s have a `pricePerUnit` property: 

> The `pricePerUnit` specifies the cost of a single _unit_ in a single _period_.
>
> Products not paid with credits must have a `pricePerUnit` of 1. Quotas and one-time payments are always made for a 
> single "period". 

This can lead to some surprising results when defining compute products. Let's consider the example from 
the beginning:

| Name | vCPU | RAM (GB) | GPU | Price |
|------|------|----------|-----|-------|
| `example-slim-1` | 1 | 4 | 0 | 0,100 DKK/hr |
| `example-slim-2` | 2 | 8 | 0 | 0,200 DKK/hr |
| `example-slim-4` | 4 | 16 | 0 | 0,400 DKK/hr |
| `example-slim-8` | 8 | 32 | 0 | 0,800 DKK/hr |

__Table:__ Human-readable compute products.

When implementing this in UCloud, a Provider might naively set the following prices:

| Name | ChargeType | ProductPriceUnit | Price |
|------|------------|------------------|-------|
| `example-slim-1` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `100_000` |
| `example-slim-2` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `200_000` |
| `example-slim-4` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `400_000` |
| `example-slim-8` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `800_000` |

__Table:__ ️⚠️ Incorrect implementation of prices in UCloud ️⚠️

__This is wrong.__ UCloud defines the price as the cost of using a single unit in a single period. The "unit of use" 
for a compute product is a single vCPU.  Thus, a correct $Provider implementation would over-report the usage by a 
factor equal to the number of vCPUs in the machine. Instead, the price must be based on a single vCPU:

| Name | ChargeType | ProductPriceUnit | Price |
|------|------------|------------------|-------|
| `example-slim-1` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `100_000` |
| `example-slim-2` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `100_000` |
| `example-slim-4` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `100_000` |
| `example-slim-8` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `100_000` |

__Table:__ ✅ Correct implementation of prices in UCloud ✅
        """.trimIndent()
    }

    private const val browseUseCase = "browse"
    private const val browseByTypeUseCase = "browse-by-type"
    private const val retrieveUseCase = "retrieve"

    override fun documentation() {
        useCase(
            browseUseCase,
            "Browse all available products",
            flow = {
                val user = basicUser()
                success(
                    browse,
                    ProductsBrowseRequest(itemsPerPage = 50),
                    ProductsBrowseResponse(
                        50,
                        listOf(
                            Product.Compute(
                                "example-compute",
                                1_000_000,
                                ProductCategoryId("example-compute", "example"),
                                "An example compute product",
                                cpu = 10,
                                memoryInGigs = 20,
                                gpu = 0,
                                unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE
                            ),
                            Product.Storage(
                                "example-storage",
                                1,
                                ProductCategoryId("example-storage", "example"),
                                "An example storage product (Quota)",
                                chargeType = ChargeType.DIFFERENTIAL_QUOTA,
                                unitOfPrice = ProductPriceUnit.PER_UNIT
                            )
                        ),
                        null
                    ),
                    user
                )
            }
        )

        useCase(
            browseByTypeUseCase,
            "Browse products by type",
            flow = {
                val user = basicUser()
                success(
                    browse,
                    ProductsBrowseRequest(itemsPerPage = 50, filterArea = ProductType.COMPUTE),
                    ProductsBrowseResponse(
                        50,
                        listOf(
                            Product.Compute(
                                "example-compute",
                                1_000_000,
                                ProductCategoryId("example-compute", "example"),
                                "An example compute product",
                                cpu = 10,
                                memoryInGigs = 20,
                                gpu = 0,
                                unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE
                            ),
                        ),
                        null
                    ),
                    user
                )
            }
        )

        useCase(
            retrieveUseCase,
            "Retrieve a single product",
            flow = {
                val user = basicUser()
                success(
                    retrieve,
                    ProductsRetrieveRequest(
                        filterName = "example-compute",
                        filterCategory = "example-compute",
                        filterProvider = "example"
                    ),
                    Product.Compute(
                        "example-compute",
                        1_000_000,
                        ProductCategoryId("example-compute", "example"),
                        "An example compute product",
                        cpu = 10,
                        memoryInGigs = 20,
                        gpu = 0,
                        unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE
                    ),
                    user
                )
            }
        )
    }

    val create = call("create", BulkRequest.serializer(Product.serializer()), Unit.serializer(), CommonErrorMessage.serializer()) {
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
                
                If the $TYPE_REF Product already exists, then a new `version` of it is created. Version numbers are
                always sequential and the incoming version number is always ignored by UCloud.
            """.trimIndent()
        }
    }

    val retrieve = call("retrieve", ProductsRetrieveRequest.serializer(), Product.serializer(), CommonErrorMessage.serializer()) {
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
                    "of $TYPE_REF Product ."
                useCaseReference(browseUseCase, "Browse in the full product catalog")
                useCaseReference(browseByTypeUseCase, "Browse for a specific type of product (e.g. compute)")
            }
        },
        ProductsBrowseRequest.serializer(),
        PageV2.serializer(Product.serializer()),
        CommonErrorMessage.serializer(),
        typeOfIfPossible<ProductsBrowseRequest>(),
        typeOfIfPossible<ProductsBrowseResponse>(),
        typeOfIfPossible<CommonErrorMessage>()
    )
}
