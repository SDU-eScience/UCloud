package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.native.concurrent.ThreadLocal
import kotlin.reflect.typeOf

const val UCLOUD_PROVIDER = "ucloud"

@Deprecated("Replace with ProductType", ReplaceWith("ProductType"))
typealias ProductArea = ProductType

@Serializable
enum class ProductType {
    STORAGE,
    COMPUTE,
    INGRESS,
    LICENSE,
    NETWORK_IP
}

@Serializable
data class ProductCategory(
    val id: ProductCategoryId,
    val productType: ProductType,
    val chargeType: ChargeType = ChargeType.ABSOLUTE,
) {
    @Deprecated("Renamed to productType")
    val area: ProductType get() = productType
}

@Serializable
enum class ChargeType {
    ABSOLUTE,
    DIFFERENTIAL_QUOTA
}

enum class ProductPriceUnit {
    PER_MINUTE,
    PER_HOUR,
    PER_DAY,
    PER_WEEK,
    PER_UNIT
}

@Serializable
data class ProductCategoryId(
    val name: String,
    val provider: String
) {
    @Deprecated("Renamed to name", ReplaceWith("name"))
    val id: String get() = name
}

@Serializable
@UCloudApiDoc("Contains a unique reference to a [Product](/backend/accounting-service/README.md)")
data class ProductReference(
    @UCloudApiDoc("The `Product` ID")
    val id: String,
    @UCloudApiDoc("The ID of the `Product`'s category")
    val category: String,
    @UCloudApiDoc("The provider of the `Product`")
    val provider: String,
)

typealias CreateProductRequest = Product
typealias CreateProductResponse = Unit

typealias UpdateProductRequest = Product
typealias UpdateProductResponse = Unit

@Serializable
sealed class Product {
    abstract val category: ProductCategoryId
    abstract val pricePerUnit: Long
    abstract val name: String
    abstract val description: String
    abstract val hiddenInGrantApplications: Boolean
    abstract val priority: Int
    abstract val version: Int
    abstract val freeToUse: Boolean
    abstract val productType: ProductType
    abstract val unitOfPrice: ProductPriceUnit


    @Deprecated("Replace with name", ReplaceWith("name"))
    val id: String get() = name

    @UCloudApiDoc("Included only with certain endpoints which support `includeBalance`")
    var balance: Long? = null

    @Deprecated("productType", ReplaceWith("productType"))
    val area: ProductArea
        get() = productType

    @Serializable
    @SerialName("storage")
    data class Storage(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val priority: Int = 0,
        override val version: Int,
        override val freeToUse: Boolean,
        override val unitOfPrice: ProductPriceUnit,
    ) : Product() {
        override val productType: ProductType = ProductType.STORAGE
        init {
            require(pricePerUnit >= 0)
            require(name.isNotBlank())
            require(description.count { it == '\n' } == 0)
        }
    }

    @Serializable
    @SerialName("compute")
    data class Compute(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val priority: Int = 0,
        val cpu: Int? = null,
        val memoryInGigs: Int? = null,
        val gpu: Int? = null,
        override val version: Int,
        override val freeToUse: Boolean,
        override val unitOfPrice: ProductPriceUnit,
    ) : Product() {
        override val productType: ProductType = ProductType.COMPUTE

        init {
            require(pricePerUnit >= 0)
            require(name.isNotBlank())
            require(description.count { it == '\n' } == 0)

            if (gpu != null) require(gpu >= 0) { "gpu is negative ($this)" }
            if (cpu != null) require(cpu >= 0) { "cpu is negative ($this)" }
            if (memoryInGigs != null) require(memoryInGigs >= 0) { "memoryInGigs is negative ($this)" }
        }
    }

    @Serializable
    @SerialName("ingress")
    data class Ingress(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val priority: Int = 0,
        override val version: Int,
        override val freeToUse: Boolean,
        override val unitOfPrice: ProductPriceUnit,
    ) : Product() {
        override val productType: ProductType = ProductType.INGRESS
        init {
            require(pricePerUnit >= 0)
            require(name.isNotBlank())
            require(description.count { it == '\n' } == 0)
        }
    }

    @Serializable
    @SerialName("license")
    data class License(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val priority: Int = 0,
        val tags: List<String> = emptyList(),
        override val version: Int,
        override val freeToUse: Boolean,
        override val unitOfPrice: ProductPriceUnit,
    ) : Product() {
        override val productType: ProductType = ProductType.LICENSE
        init {
            require(pricePerUnit >= 0)
            require(name.isNotBlank())
            require(description.count { it == '\n' } == 0)
        }
    }

    @Serializable
    @SerialName("network_ip")
    data class NetworkIP(
        override val name: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val priority: Int = 0,
        override val version: Int,
        override val freeToUse: Boolean,
        override val unitOfPrice: ProductPriceUnit,
    ) : Product() {
        override val productType: ProductType = ProductType.NETWORK_IP
        init {
            require(pricePerUnit >= 0)
            require(name.isNotBlank())
            require(description.count { it == '\n' } == 0)
        }
    }
}

@Serializable
data class FindProductRequest(
    val provider: String,
    val productCategory: String,
    val product: String
)

typealias FindProductResponse = Product

@Serializable
data class ListProductsRequest(
    val provider: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias ListProductsResponse = Page<Product>

@Serializable
data class ListProductsByAreaRequest(
    val provider: String,
    val area: ProductArea,
    val showHidden: Boolean = true,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias ListProductsByAreaResponse = Page<Product>

@Serializable
data class RetrieveAllFromProviderRequest(val provider: String, val showHidden: Boolean = true)
typealias RetrieveAllFromProviderResponse = List<Product>

interface ProductFilters {
    val filterArea: ProductArea?
    val filterProvider: String?
    val filterUsable: Boolean?
    val filterCategory: String?
}

interface ProductFlags {
    val includeBalance: Boolean?
}

@Serializable
data class ProductsBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,

    override val filterProvider: String? = null,
    override val filterArea: ProductArea? = null,
    override val filterUsable: Boolean? = null,
    override val filterCategory: String? = null,

    override val includeBalance: Boolean? = null
) : WithPaginationRequestV2, ProductFilters, ProductFlags
typealias ProductsBrowseResponse = PageV2<Product>

@OptIn(ExperimentalStdlibApi::class)
@ThreadLocal
object Products : CallDescriptionContainer("products") {
    const val baseContext = "/api/products"

    init {
        serializerLookupTable = mapOf(
            serializerEntry(Product.serializer())
        )
    }

    /**
     * Creates a new [Product]
     *
     * Note that only the provider themselves are allowed to push a new [Product] to the database. A matching
     * [ProductCategory] is automatically created when the first [Product] in that category is created.
     */
    val createProduct = call<CreateProductRequest, CreateProductResponse, CommonErrorMessage>("createProduct") {
        httpCreate(baseContext, roles = setOf(Role.SERVICE, Role.ADMIN, Role.PROVIDER))
    }

    val updateProduct = call<UpdateProductRequest, UpdateProductResponse, CommonErrorMessage>("updateProduct") {
        httpUpdate(baseContext, "update", roles = Roles.AUTHENTICATED)
    }

    val findProduct = call<FindProductRequest, FindProductResponse, CommonErrorMessage>("findProduct") {
        httpRetrieve(baseContext, roles = Roles.AUTHENTICATED)
    }

    @Deprecated("Switch to `browse`")
    val listProductsByType =
        call<ListProductsByAreaRequest, ListProductsByAreaResponse, CommonErrorMessage>(
            "listProductionsByType",
            {
                auth {
                    access = AccessRight.READ
                    roles = Roles.PUBLIC
                }

                http {
                    method = HttpMethod.Get

                    path {
                        using(baseContext)
                        +"listByArea"
                    }

                    params {
                        +boundTo(ListProductsByAreaRequest::provider)
                        +boundTo(ListProductsByAreaRequest::area)
                        +boundTo(ListProductsByAreaRequest::itemsPerPage)
                        +boundTo(ListProductsByAreaRequest::page)
                        +boundTo(ListProductsByAreaRequest::showHidden)
                    }
                }
            },
            ListProductsByAreaRequest.serializer(),
            Page.serializer(Product.serializer()),
            CommonErrorMessage.serializer(),
            typeOf<ListProductsByAreaRequest>(),
            typeOf<ListProductsByAreaResponse>(),
            typeOf<CommonErrorMessage>()
        )

    @Deprecated("Switch to `browse`")
    val listProducts = call<ListProductsRequest, ListProductsResponse, CommonErrorMessage>(
        "listProducts",
        {
            auth {
                access = AccessRight.READ
                roles = Roles.AUTHENTICATED
            }

            http {
                method = HttpMethod.Get

                path {
                    using(baseContext)
                    +"list"
                }

                params {
                    +boundTo(ListProductsRequest::provider)
                    +boundTo(ListProductsRequest::itemsPerPage)
                    +boundTo(ListProductsRequest::page)
                }
            }
        },
        ListProductsRequest.serializer(),
        Page.serializer(Product.serializer()),
        CommonErrorMessage.serializer(),
        typeOf<ListProductsRequest>(),
        typeOf<ListProductsResponse>(),
        typeOf<CommonErrorMessage>()
    )

    @Deprecated("Use with caution. This call will likely be replaced with a paginated call (i.e. `browse`)")
    val retrieveAllFromProvider =
        call<RetrieveAllFromProviderRequest, RetrieveAllFromProviderResponse, CommonErrorMessage>(
            "retrieveAllFromProvider",
            {
                auth {
                    access = AccessRight.READ
                    roles = Roles.AUTHENTICATED
                }

                http {
                    method = HttpMethod.Get

                    path {
                        using(baseContext)
                        +"retrieve"
                    }

                    params {
                        +boundTo(RetrieveAllFromProviderRequest::provider)
                        +boundTo(RetrieveAllFromProviderRequest::showHidden)
                    }
                }
            },
            RetrieveAllFromProviderRequest.serializer(),
            ListSerializer(Product.serializer()),
            CommonErrorMessage.serializer(),
            typeOf<RetrieveAllFromProviderRequest>(),
            typeOf<RetrieveAllFromProviderResponse>(),
            typeOf<CommonErrorMessage>()
        )

    val browse = call<ProductsBrowseRequest, ProductsBrowseResponse, CommonErrorMessage>(
        "browse",
        {
            httpBrowse(baseContext)
        },
        ProductsBrowseRequest.serializer(),
        PageV2.serializer(Product.serializer()),
        CommonErrorMessage.serializer(),
        typeOf<ProductsBrowseRequest>(),
        typeOf<ProductsBrowseResponse>(),
        typeOf<CommonErrorMessage>()
    )
}
