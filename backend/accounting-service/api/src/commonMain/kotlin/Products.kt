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

@Serializable
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
        override val priority: Int = 0,
        override val version: Int = 1,
        override val freeToUse: Boolean = false,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.PER_DAY,
        override val chargeType: ChargeType = ChargeType.ABSOLUTE,
        override val hiddenInGrantApplications: Boolean = false,
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
        override val priority: Int = 0,
        val cpu: Int? = null,
        val memoryInGigs: Int? = null,
        val gpu: Int? = null,
        override val version: Int = 1,
        override val freeToUse: Boolean = false,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.PER_MINUTE,
        override val chargeType: ChargeType = ChargeType.ABSOLUTE,
        override val hiddenInGrantApplications: Boolean = false,
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
        override val priority: Int = 0,
        override val version: Int = 1,
        override val freeToUse: Boolean = false,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.PER_UNIT,
        override val chargeType: ChargeType = ChargeType.ABSOLUTE,
        override val hiddenInGrantApplications: Boolean = false,
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
        override val priority: Int = 0,
        override val version: Int = 1,
        override val freeToUse: Boolean = false,
        override val unitOfPrice: ProductPriceUnit = ProductPriceUnit.PER_UNIT,
        override val chargeType: ChargeType = ChargeType.ABSOLUTE,
        override val hiddenInGrantApplications: Boolean = false,
    ) : Product() {
        override val productType: ProductType = ProductType.NETWORK_IP
        init {
            require(pricePerUnit >= 0)
            require(name.isNotBlank())
            require(description.count { it == '\n' } == 0)
        }
    }
}

interface ProductFlags {
    val filterName: String?
    val filterArea: ProductType?
    val filterProvider: String?
    val filterUsable: Boolean?
    val filterCategory: String?
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
    override val filterUsable: Boolean? = null,
    override val filterCategory: String? = null,

    override val includeBalance: Boolean? = null
) : WithPaginationRequestV2, ProductFlags
typealias ProductsBrowseResponse = PageV2<Product>

@Serializable
data class ProductsRetrieveRequest(
    override val filterName: String,
    override val filterCategory: String,
    override val filterProvider: String,

    override val filterArea: ProductType? = null,
    override val filterUsable: Boolean? = null,
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

    val create = call<BulkRequest<Product>, Unit, CommonErrorMessage>("create") {
        httpCreate(baseContext, roles = setOf(Role.SERVICE, Role.ADMIN, Role.PROVIDER))
    }

    val retrieve = call<ProductsRetrieveRequest, Product, CommonErrorMessage>("retrieve") {
        httpRetrieve(baseContext, roles = Roles.AUTHENTICATED)
    }

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
