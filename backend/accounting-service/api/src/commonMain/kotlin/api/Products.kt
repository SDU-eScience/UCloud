package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

const val UCLOUD_PROVIDER = "ucloud"

@Serializable
enum class ProductType {
    STORAGE,
    COMPUTE,
    INGRESS,
    LICENSE,
    NETWORK_IP
}

@Serializable
enum class ChargeType {
    ABSOLUTE,
    DIFFERENTIAL_QUOTA
}

@Serializable
data class ProductCategory(
    val id: ProductCategoryId,
    val productType: ProductType,
    val chargeType: ChargeType
)

@Serializable
data class ProductCategoryId(
    val name: String,
    val provider: String
)

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

    @UCloudApiDoc("Included only with certain endpoints which support `includeBalance`")
    var balance: Long? = null

    @Serializable
    @SerialName("storage")
    data class Storage(
        override val name: String,
        override val version: Int,
        override val pricePerUnit: Long,
        override val unitOfPrice: ProductPriceUnit,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val priority: Int = 0,
        override val freeToUse: Boolean = false,
    ) : Product() {
        @Transient
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
        override val version: Int,
        override val pricePerUnit: Long,
        override val unitOfPrice: ProductPriceUnit,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val priority: Int = 0,
        override val freeToUse: Boolean = false,
        val cpu: Int? = null,
        val memoryInGigs: Int? = null,
        val gpu: Int? = null
    ) : Product() {
        @Transient
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
        override val version: Int,
        override val pricePerUnit: Long,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.PER_UNIT,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val priority: Int = 0,
        override val freeToUse: Boolean,
    ) : Product() {
        @Transient
        override val productType = ProductType.INGRESS

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
        override val version: Int,
        override val pricePerUnit: Long,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.PER_UNIT,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val priority: Int = 0,
        override val freeToUse: Boolean = true,
        val tags: List<String> = emptyList(),
    ) : Product() {
        @Transient
        override val productType = ProductType.LICENSE

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
        override val version: Int,
        override val pricePerUnit: Long,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.PER_UNIT,
        override val category: ProductCategoryId,
        override val description: String = "",
        override val hiddenInGrantApplications: Boolean = false,
        override val priority: Int = 0,
        override val freeToUse: Boolean = false
    ) : Product() {
        @Transient
        override val productType = ProductType.NETWORK_IP

        init {
            require(pricePerUnit >= 0)
            require(name.isNotBlank())
            require(description.count { it == '\n' } == 0)
        }
    }
}

@Serializable
enum class ProductPriceUnit {
    PER_MINUTE,
    PER_HOUR,
    PER_DAY,
    PER_WEEK,
    PER_UNIT
}

@Serializable
data class FindProductRequest(
    val provider: String,
    val productCategory: String,
    val product: String
)

typealias FindProductResponse = Product

interface ProductFilters {
    val filterArea: ProductType?
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
    override val filterArea: ProductType? = null,
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
    val createProduct = call<BulkRequest<CreateProductRequest>, CreateProductResponse, CommonErrorMessage>("createProduct") {
        httpCreate(
            baseContext,
            setOf(Role.SERVICE, Role.ADMIN, Role.PROVIDER)
        )
    }

    val updateProduct = call<BulkRequest<UpdateProductRequest>, UpdateProductResponse, CommonErrorMessage>("updateProduct") {
        httpUpdate(
            baseContext,
            "update",
            Roles.AUTHENTICATED
        )
    }

    val findProduct = call<FindProductRequest, FindProductResponse, CommonErrorMessage>("findProduct") {
        httpRetrieve(
            baseContext,
            "product",
            Roles.AUTHENTICATED
        )
    }

    val browse = call<ProductsBrowseRequest, ProductsBrowseResponse, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }
}
