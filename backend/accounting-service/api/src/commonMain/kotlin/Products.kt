package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.native.concurrent.ThreadLocal
import kotlin.reflect.typeOf

const val UCLOUD_PROVIDER = "ucloud"

@Deprecated("Replace with ProductType", ReplaceWith("ProductType"))
typealias ProductArea = ProductType

@Serializable
@UCloudApiOwnedBy(Products::class)
enum class ProductType {
    STORAGE,
    COMPUTE,
    INGRESS,
    LICENSE,
    NETWORK_IP
}

@Serializable
@UCloudApiOwnedBy(Products::class)
data class ProductCategory(
    val id: ProductCategoryId,
    val productType: ProductType,
    val chargeType: ChargeType = ChargeType.ABSOLUTE,
) {
    @Deprecated("Renamed to productType")
    val area: ProductType get() = productType
}

@Serializable
@UCloudApiOwnedBy(Products::class)
enum class ChargeType {
    ABSOLUTE,
    DIFFERENTIAL_QUOTA
}

@UCloudApiOwnedBy(Products::class)
enum class ProductPriceUnit {
    @UCloudApiDoc("""
        Used for resources which either: are charged once for the entire life-time (`ChargeType.ABSOLUTE`) or
        used to enforce a quota (`ChargeType.DIFFERENTIAL_QUOTA`).
        
        When used in combination with `ChargeType.ABSOLUTE` then this is typically used for resources such as:
        licenses, public IPs and links.
        
        When used in combination with `ChargeType.DIFFERENTIAL_QUOTA` it is used to enforce a quota. At any point in
        time, a user should never be allowed to use more units than specified in their current `balance`. For example,
        if `balance = 100` and `ProductType = ProductType.COMPUTE` then no more than 100 jobs should be allowed to run
        at any given point in time. Similarly, this can be used to enforce a storage quota.
    """)
    PER_UNIT,

    @UCloudApiDoc("""
        Used for resources which are charged periodically in a pre-defined currency.
        
        This `ProductPriceUnit` can only be used in combination with `ChargeType.ABSOLUTE`.
        
        The pre-defined currency is decided between the UCloud/Core and the provider out-of-band. UCloud only supports
        a single currency for the entire system.
        
        The accounting system only stores balances as an integer, for precision reasons. As a result, a charge using
        `CREDITS_PER_X` will always refer to one-millionth of the currency (for example: 1 Credit = 0.000001 DKK).
        
        This price unit comes in several variants: `MINUTE`, `HOUR`, `DAY`. This period is used to define the `units`
        of a `charge`. For example, if a `charge` is made on a product with `CREDITS_PER_MINUTE` then the `units`
        property refer to the number of minutes which have elapsed. The provider is not required to perform `charge`
        operations this often, it only serves to define the true meaning of `units` in the `charge` operation.
        
        The period used SHOULD always refer to monotonically increasing time. In practice, this means that a user should
        not be charged differently because of summer/winter time.
    """)
    CREDITS_PER_MINUTE,
    @UCloudApiDoc("See `CREDITS_PER_MINUTE`")
    CREDITS_PER_HOUR,
    @UCloudApiDoc("See `CREDITS_PER_MINUTE`")
    CREDITS_PER_DAY,

    @UCloudApiDoc("""
        Used for resources which are charged periodically.
        
        This `ProductPriceUnit` can only be used in combination with `ChargeType.ABSOLUTE`.
        
        All allocations granted to a product of `UNITS_PER_X` specify the amount of units of the recipient can use.
        Some examples include:
        
          - Core hours
          - Public IP hours
          
        This price unit comes in several variants: `MINUTE`, `HOUR`, `DAY`. This period is used to define the `units`
        of a `charge`. For example, if a `charge` is made on a product with `CREDITS_PER_MINUTE` then the `units`
        property refer to the number of minutes which have elapsed. The provider is not required to perform `charge`
        operations this often, it only serves to define the true meaning of `units` in the `charge` operation.
        
        The period used SHOULD always refer to monotonically increasing time. In practice, this means that a user should
        not be charged differently because of summer/winter time.
    """)
    UNITS_PER_MINUTE,
    @UCloudApiDoc("See `UNITS_PER_MINUTE`")
    UNITS_PER_HOUR,
    @UCloudApiDoc("See `UNITS_PER_MINUTE`")
    UNITS_PER_DAY,
}

@Serializable
@UCloudApiOwnedBy(Products::class)
data class ProductCategoryId(
    val name: String,
    val provider: String
) {
    @Deprecated("Renamed to name", ReplaceWith("name"))
    val id: String get() = name
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
)

@Serializable
@UCloudApiOwnedBy(Products::class)
sealed class Product {
    abstract val category: ProductCategoryId
    abstract val pricePerUnit: Long
    abstract val name: String
    abstract val description: String
    abstract val priority: Int
    abstract val version: Int
    abstract val freeToUse: Boolean
    abstract val productType: ProductType
    abstract val unitOfPrice: ProductPriceUnit
    abstract val chargeType: ChargeType
    abstract val hiddenInGrantApplications: Boolean

    @Deprecated("Replace with name", ReplaceWith("name"))
    val id: String get() = name

    @UCloudApiDoc("Included only with certain endpoints which support `includeBalance`")
    var balance: Long? = null

    protected fun verify() {
        checkMinimumValue(::pricePerUnit, pricePerUnit, 0)
        checkSingleLine(::name, name)
        checkSingleLine(::description, description)

        when (unitOfPrice) {
            ProductPriceUnit.UNITS_PER_MINUTE,
            ProductPriceUnit.UNITS_PER_HOUR,
            ProductPriceUnit.UNITS_PER_DAY -> {
                if (chargeType == ChargeType.DIFFERENTIAL_QUOTA) {
                    throw RPCException("UNITS_PER_X cannot be used with DIFFERENTIAL_QUOTA", HttpStatusCode.BadRequest)
                }
            }

            ProductPriceUnit.CREDITS_PER_MINUTE,
            ProductPriceUnit.CREDITS_PER_HOUR,
            ProductPriceUnit.CREDITS_PER_DAY -> {
                if (chargeType == ChargeType.DIFFERENTIAL_QUOTA) {
                    throw RPCException("CREDITS_PER_X cannot be used with DIFFERENTIAL_QUOTA",
                        HttpStatusCode.BadRequest)
                }
            }

            ProductPriceUnit.PER_UNIT -> {
                // OK
            }
        }
    }

    @Serializable
    @SerialName("storage")
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
    }

    @Serializable
    @SerialName("compute")
    data class Compute(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val priority: Int = 0,
        val cpu: Int? = null,
        val memoryInGigs: Int? = null,
        val gpu: Int? = null,
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
        }
    }

    @Serializable
    @SerialName("ingress")
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
    }

    @Serializable
    @SerialName("license")
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
    }

    @Serializable
    @SerialName("network_ip")
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
@ThreadLocal
object Products : CallDescriptionContainer("products") {
    const val baseContext = "/api/products"

    init {
        serializerLookupTable = mapOf(
            serializerEntry(Product.serializer())
        )
    }

    private const val browseUseCase = "browse"
    private const val browseByTypeUseCase = "browse-by-type"
    private const val retrieveUseCase = "retrieve"

    override fun documentation() {
        useCase(
            browseUseCase,
            "Browse all available components",
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

    val create = call<BulkRequest<Product>, Unit, CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = setOf(Role.SERVICE, Role.ADMIN, Role.PROVIDER))

        documentation {
            summary = "Creates a new $TYPE_REF Product in UCloud"
            description = """
                Only providers and UCloud administrators can create a $TYPE_REF Product . When this endpoint is
                invoked by a provider, then the provider field of the $TYPE_REF Product must match the invoking user.
                
                The $TYPE_REF Product will become ready and visible in UCloud immediately after invoking this call.
                If no $TYPE_REF Product has been created in this category before, then this category will be created.
                
                ---
                
                __📝 NOTE:__ Must properties of a $TYPE_REF ProductCategory are immutable and must not be changed.
                As a result, you cannot create a new $TYPE_REF Product later with different category properties.
                
                ---
                
                If the $TYPE_REF Product already exists, then a new `version` of it is created. Version numbers are
                always sequential and the incoming version number is always ignored by UCloud.
            """.trimIndent()
        }
    }

    val retrieve = call<ProductsRetrieveRequest, Product, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.AUTHENTICATED)

        documentation {
            summary = "Retrieve a single product"
            useCaseReference(retrieveUseCase, "Retrieving a single product by ID")
        }
    }

    val browse = call<ProductsBrowseRequest, ProductsBrowseResponse, CommonErrorMessage>(
        "browse",
        {
            httpBrowse(baseContext, roles = Roles.PUBLIC)

            documentation {
                summary = "Browse a set of products"
                description = "This endpoint uses the normal pagination and filter mechanisms to return a list " +
                    "of $TYPE_REF Product ."
                useCaseReference(browseUseCase, "Browse in the full product catalogue")
                useCaseReference(browseByTypeUseCase, "Browse for a specific type of product (e.g. compute)")
            }
        },
        ProductsBrowseRequest.serializer(),
        PageV2.serializer(Product.serializer()),
        CommonErrorMessage.serializer(),
        typeOf<ProductsBrowseRequest>(),
        typeOf<ProductsBrowseResponse>(),
        typeOf<CommonErrorMessage>()
    )
}
