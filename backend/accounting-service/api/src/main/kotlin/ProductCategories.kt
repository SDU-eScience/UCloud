package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.messages.BinaryAllocator
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class AccountingUnit(
    val name: String,           //e.g. gigabyte
    val namePlural: String,     //e.g. gigabytes
    val floatingPoint: Boolean,
    val displayFrequencySuffix: Boolean
)

@Serializable
enum class AccountingFrequency {
    ONCE,
    PERIODIC_MINUTE,
    PERIODIC_HOUR,
    PERIODIC_DAY;

    companion object {
        public fun fromValue(value: String): AccountingFrequency = when (value) {
            "ONCE" -> ONCE
            "PERIODIC_MINUTE" -> PERIODIC_MINUTE
            "PERIODIC_HOUR" -> PERIODIC_HOUR
            "PERIODIC_DAY" -> PERIODIC_DAY
            else -> throw IllegalArgumentException()
        }

        public fun toMinutes(value: String): Long = when (value) {
            "ONCE" -> 1L
            "PERIODIC_MINUTE" -> 60L
            "PERIODIC_HOUR" -> 60 * 60L
            "PERIODIC_DAY" -> 60 * 60 * 24L
            else -> throw IllegalArgumentException()
        }
    }
}

private val periodicalFrequencies = listOf<AccountingFrequency>(
    AccountingFrequency.PERIODIC_MINUTE,
    AccountingFrequency.PERIODIC_HOUR,
    AccountingFrequency.PERIODIC_DAY
)

@Serializable
data class AccountingUnitConversion(
    @Contextual
    val factor: BigDecimal,
    val destinationUnit: AccountingUnit
)

@Serializable
data class ProductCategory(
    val name: String,                   //e.g. u1-cephfs
    val provider: String,               //e.g. UCloud
    val productType: ProductType,       //e.g. STORAGE
    val accountingUnit: AccountingUnit,
    val accountingFrequency: AccountingFrequency,
    val conversionTable: List<AccountingUnitConversion> = emptyList(),
    @UCloudApiDoc(
        """
        Indicates that a Wallet is not required to use this Product category
        
        Under normal circumstances, a $TYPE_REF Wallet is always required. This is required even if a $TYPE_REF Product
        has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.
    """
    )
    val freeToUse: Boolean = false
) {
    fun isPeriodic(): Boolean = periodicalFrequencies.contains(accountingFrequency)
}

fun ProductCategory.toBinary(allocator: BinaryAllocator): ProductCategoryB = with(allocator) {
    return ProductCategoryB(
        name = name,
        provider = provider,
        productType = when (productType) {
            ProductType.STORAGE -> ProductTypeB.STORAGE
            ProductType.COMPUTE -> ProductTypeB.COMPUTE
            ProductType.INGRESS -> ProductTypeB.INGRESS
            ProductType.LICENSE -> ProductTypeB.LICENSE
            ProductType.NETWORK_IP -> ProductTypeB.NETWORK_IP
        },
        accountingUnit = AccountingUnitB(
            name = accountingUnit.name,
            namePlural = accountingUnit.namePlural,
            floatingPoint = accountingUnit.floatingPoint,
            displayFrequencySuffix = accountingUnit.displayFrequencySuffix,
        ),
        accountingFrequency = when (accountingFrequency) {
            AccountingFrequency.ONCE -> AccountingFrequencyB.ONCE
            AccountingFrequency.PERIODIC_MINUTE -> AccountingFrequencyB.PERIODIC_MINUTE
            AccountingFrequency.PERIODIC_HOUR -> AccountingFrequencyB.PERIODIC_HOUR
            AccountingFrequency.PERIODIC_DAY -> AccountingFrequencyB.PERIODIC_DAY
        },
        freeToUse = freeToUse,
    )
}


@UCloudApiOwnedBy(ProductCategories::class)
@UCloudApiStable
@Serializable
data class ProductCategoryIdV2(
    val name: String,
    val provider: String
) : DocVisualizable {
    override fun visualize(): DocVisualization {
        return DocVisualization.Inline("$name / $provider")
    }
}

interface ProductCategoryFlags {
    val filterName: String?
    val filterProductType: ProductType?
    val filterProvider: String?
    val filterCategory: String?
    val includeBalance: Boolean?
    val includeMaxBalance: Boolean?
}

@Serializable
data class ProductCategoriesBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,

    override val filterName: String? = null,
    override val filterProvider: String? = null,
    override val filterProductType: ProductType? = null,
    override val filterCategory: String? = null,

    override val includeBalance: Boolean? = null,
    override val includeMaxBalance: Boolean? = null
) : WithPaginationRequestV2, ProductCategoryFlags
typealias ProductCategoriesBrowseResponse = PageV2<ProductCategory>

@Serializable
data class ProductCategoryRetrieveRequest(
    override val filterName: String,
    override val filterCategory: String,
    override val filterProvider: String,

    override val filterProductType: ProductType? = null,
    override val filterUsable: Boolean? = null,

    override val includeBalance: Boolean? = null,
    override val includeMaxBalance: Boolean? = null,
) : ProductFlagsV2

object ProductCategories : CallDescriptionContainer("product_categories") {
    const val baseContext = "/api/product_categories"

    val retrieve = call(
        "retrieve",
        ProductCategoryRetrieveRequest.serializer(),
        ProductCategory.serializer(),
        CommonErrorMessage.serializer()
    ) {
        httpRetrieve(baseContext, roles = Roles.AUTHENTICATED)

        documentation {
            summary = "Retrieve a single product category"
        }
    }

    val browse = call(
        "browse",
        {
            httpBrowse(baseContext, roles = Roles.PUBLIC)

            documentation {
                summary = "Browse a set of products categories"
                description = "This endpoint uses the normal pagination and filter mechanisms to return a list " +
                        "of $TYPE_REF ProductCategory ."
            }
        },
        ProductCategoriesBrowseRequest.serializer(),
        PageV2.serializer(ProductCategory.serializer()),
        CommonErrorMessage.serializer(),
        typeOfIfPossible<ProductCategoriesBrowseRequest>(),
        typeOfIfPossible<ProductCategoriesBrowseResponse>(),
        typeOfIfPossible<CommonErrorMessage>()
    )
}
