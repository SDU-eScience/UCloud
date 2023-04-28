package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.Contextual
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
    PERIODIC_DAY
}

private val periodicalFrequencies = listOf<AccountingFrequency>(
    AccountingFrequency.PERIODIC_DAY,
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
    val conversionTable: List<AccountingUnitConversion>
) {
    fun isPeriodic(): Boolean = periodicalFrequencies.contains(accountingFrequency)
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

    override val includeBalance: Boolean? = null,
    override val includeMaxBalance: Boolean? = null,
) : ProductFlagsV2

object ProductCategories : CallDescriptionContainer("product_categories") {
    const val baseContext = "/api/product_categories"

    val retrieve = call("retrieve", ProductCategoryRetrieveRequest.serializer(), ProductCategory.serializer(), CommonErrorMessage.serializer()) {
        httpRetrieve(baseContext, roles = Roles.AUTHENTICATED)

        documentation {
            summary = "Retrieve a single product"
        }
    }

    val browse = call(
        "browse",
        {
            httpBrowse(baseContext, roles = Roles.PUBLIC)

            documentation {
                summary = "Browse a set of products"
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
