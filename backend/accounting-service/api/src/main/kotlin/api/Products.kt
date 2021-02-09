package dk.sdu.cloud.accounting.api

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.service.*
import io.ktor.http.HttpMethod

const val UCLOUD_PROVIDER = "ucloud"

enum class ProductArea {
    STORAGE,
    COMPUTE,
    INGRESS,
    LICENSE
}

data class ProductCategory(
    val id: ProductCategoryId,
    val area: ProductArea
)

data class ProductCategoryId(
    val id: String,
    val provider: String
)

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

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ProductAvailability.Available::class, name = "available"),
    JsonSubTypes.Type(value = ProductAvailability.Unavailable::class, name = "unavailable")
)
sealed class ProductAvailability {
    class Available : ProductAvailability() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return true
        }

        override fun hashCode(): Int {
            return javaClass.hashCode()
        }
    }

    data class Unavailable(val reason: String) : ProductAvailability()
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = Product.Storage::class, name = "storage"),
    JsonSubTypes.Type(value = Product.Compute::class, name = "compute"),
    JsonSubTypes.Type(value = Product.Ingress::class, name = "ingress"),
    JsonSubTypes.Type(value = Product.License::class, name = "license"),
)
sealed class Product {
    abstract val category: ProductCategoryId
    abstract val pricePerUnit: Long
    abstract val id: String
    abstract val description: String
    abstract val hiddenInGrantApplications: Boolean
    abstract val availability: ProductAvailability
    abstract val priority: Int

    @UCloudApiDoc("Included only with certain endpoints which support `includeBalance`")
    var balance: Long? = null

    @get:JsonIgnore
    abstract val area: ProductArea

    data class Storage(
        override val id: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val availability: ProductAvailability = ProductAvailability.Available(),
        override val priority: Int = 0
    ) : Product() {
        @get:JsonIgnore
        override val area: ProductArea = ProductArea.STORAGE

        init {
            require(pricePerUnit >= 0)
            require(id.isNotBlank())
            require(description.count { it == '\n' } == 0)
        }
    }

    data class Compute(
        override val id: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val availability: ProductAvailability = ProductAvailability.Available(),
        override val priority: Int = 0,
        val cpu: Int? = null,
        val memoryInGigs: Int? = null,
        val gpu: Int? = null
    ) : Product() {
        @get:JsonIgnore
        override val area: ProductArea = ProductArea.COMPUTE

        init {
            require(pricePerUnit >= 0)
            require(id.isNotBlank())
            require(description.count { it == '\n' } == 0)

            if (gpu != null) require(gpu >= 0) { "gpu is negative ($this)" }
            if (cpu != null) require(cpu >= 0) { "cpu is negative ($this)" }
            if (memoryInGigs != null) require(memoryInGigs >= 0) { "memoryInGigs is negative ($this)" }
        }
    }

    data class Ingress(
        override val id: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val availability: ProductAvailability = ProductAvailability.Available(),
        override val priority: Int = 0,
        val paymentModel: PaymentModel = PaymentModel.PER_ACTIVATION,
    ) : Product() {
        @get:JsonIgnore
        override val area = ProductArea.INGRESS

        init {
            require(pricePerUnit >= 0)
            require(id.isNotBlank())
            require(description.count { it == '\n' } == 0)
        }
    }

    data class License(
        override val id: String,
        override val pricePerUnit: Long,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val availability: ProductAvailability = ProductAvailability.Available(),
        override val priority: Int = 0,
        val tags: List<String> = emptyList(),
        val paymentModel: PaymentModel = PaymentModel.PER_ACTIVATION,
    ) : Product() {
        @get:JsonIgnore
        override val area = ProductArea.LICENSE

        init {
            require(pricePerUnit >= 0)
            require(id.isNotBlank())
            require(description.count { it == '\n' } == 0)
        }
    }
}

enum class PaymentModel {
    @UCloudApiDoc(
        "Indicates that the product has a `pricePerUnit` of 0" +
            ", but the wallet must still receive a balance check 1 credit"
    )
    FREE_BUT_REQUIRE_BALANCE,
    PER_ACTIVATION
}

data class FindProductRequest(
    val provider: String,
    val productCategory: String,
    val product: String
)

typealias FindProductResponse = Product

data class ListProductsRequest(
    val provider: String,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias ListProductsResponse = Page<Product>

data class ListProductsByAreaRequest(
    val provider: String,
    val area: ProductArea,
    val showHidden: Boolean = true,
    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithPaginationRequest
typealias ListProductsByAreaResponse = Page<Product>

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

object Products : CallDescriptionContainer("products") {
    const val baseContext = "/api/products"

    /**
     * Creates a new [Product]
     *
     * Note that only the provider themselves are allowed to push a new [Product] to the database. A matching
     * [ProductCategory] is automatically created when the first [Product] in that category is created.
     */
    val createProduct = call<CreateProductRequest, CreateProductResponse, CommonErrorMessage>("createProduct") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.PRIVILEGED // This might need to change later for providers
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val updateProduct = call<UpdateProductRequest, UpdateProductResponse, CommonErrorMessage>("updateProduct") {
        auth {
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Post

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val findProduct = call<FindProductRequest, FindProductResponse, CommonErrorMessage>("findProduct") {
        auth {
            access = AccessRight.READ_WRITE
            roles = Roles.AUTHENTICATED
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }

            params {
                +boundTo(FindProductRequest::provider)
                +boundTo(FindProductRequest::productCategory)
                +boundTo(FindProductRequest::product)
            }
        }
    }

    @Deprecated("Switch to `browse`")
    val listProductsByType =
        call<ListProductsByAreaRequest, ListProductsByAreaResponse, CommonErrorMessage>("listProductionsByType") {
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
        }

    @Deprecated("Switch to `browse`")
    val listProducts = call<ListProductsRequest, ListProductsResponse, CommonErrorMessage>("listProducts") {
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
    }

    @Deprecated("Use with caution. This call will likely be replaced with a paginated call (i.e. `browse`)")
    val retrieveAllFromProvider =
        call<RetrieveAllFromProviderRequest, RetrieveAllFromProviderResponse, CommonErrorMessage>(
            "retrieveAllFromProvider"
        ) {
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
        }

    val browse = call<ProductsBrowseRequest, ProductsBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }
}
