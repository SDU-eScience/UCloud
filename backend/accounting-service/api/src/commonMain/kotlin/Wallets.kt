package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class WalletOwnerType {
    USER,
    PROJECT
}

@Serializable
data class WalletBalance(
    val wallet: Wallet,
    val balance: Long,
    val allocated: Long,
    val used: Long,
    val area: ProductType
)

@Serializable
data class Wallet(
    val owner: WalletOwner,
    val paysFor: ProductCategoryId,
    val allocations: List<WalletAllocation>,
    val chargePolicy: AllocationSelectorPolicy
)

/*
 * EXPIRE_FIRST takes the wallet allocation with end date closes to now.
 * ORDERED takes the wallet allocation in a user specified order.
 */
@Serializable
enum class AllocationSelectorPolicy{
    EXPIRE_FIRST,
    ORDERED
}

@Serializable
data class WalletAllocation(
    @UCloudApiDoc("A unique ID of this allocation")
    val id: String,
    @UCloudApiDoc("""A path, starting from the top, through the allocations that will be charged, when a charge is made

Note that this allocation path will always include, as its last element, this allocation.""")
    val allocationPath: List<String>,
    @UCloudApiDoc("A reference to the wallet that this allocation belongs to")
    val associatedWith: String?,
    @UCloudApiDoc("The current balance of this wallet allocation")
    val balance: Long,
    @UCloudApiDoc("The initial balance which was granted to this allocation")
    val initialBalance: Long,
    @UCloudApiDoc("Timestamp for when this allocation becomes valid")
    val startDate: Long,
    @UCloudApiDoc("Timestamp for when this allocation becomes invalid, null indicates that this allocation does not " +
        "expire automatically")
    val endDate: Long?
)

@Serializable
data class PushWalletChangeRequestItem(
    val owner: WalletOwner,
    val amount: Long,
    val productId: ProductReference,
)

@Serializable
data class WalletBrowseRequest(
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

typealias PushWalletChangeResponse = Unit

object Wallets : CallDescriptionContainer("wallets") {
    const val baseContext = "/api/accounting/wallets"

    val push = call<BulkRequest<PushWalletChangeRequestItem>, PushWalletChangeResponse, CommonErrorMessage>(
        "push"
    ) {
        httpUpdate(baseContext, "push")
    }

    val browseWallets = call<WalletBrowseRequest, PageV2<Wallet>, CommonErrorMessage>("browseWallets") {
        httpBrowse(baseContext)
    }
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
sealed class WalletOwner {
    @Serializable
    @SerialName("user")
    data class User(val username: String) : WalletOwner()

    @Serializable
    @SerialName("project")
    data class Project(val projectId: String) : WalletOwner()
}

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class ChargeWalletRequestItem(
    @UCloudApiDoc("The payer of this charge")
    val payer: WalletOwner,
    @UCloudApiDoc("""The number of units that this charge is about
        
The unit itself is defined by the product. The unit can, for example, describe that the 'units' describe the number of
minutes/hours/days.
""")
    val units: Long,
    @UCloudApiDoc("The number of products involved in this charge, for example the number of nodes")
    val numberOfProducts: Long,
    @UCloudApiDoc("A reference to the product which the service is charging for")
    val product: ProductReference,
    @UCloudApiDoc("The username of the user who generated this request")
    val performedBy: String,
    @UCloudApiDoc("A description of the charge this is used purely for presentation purposes")
    val description: String
)

typealias ChargeWalletResponse = Unit

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class DepositToWalletRequestItem(
    @UCloudApiDoc("The recipient of this deposit")
    val recipient: WalletOwner,
    @UCloudApiDoc("A reference to the source allocation which the deposit will draw from")
    val sourceAllocation: String,
    @UCloudApiDoc("The amount of credits to deposit into the recipient's wallet")
    val amount: Long,
    @UCloudApiDoc("A description of this change. This is used purely for presentation purposes.")
    val description: String,
    @UCloudApiDoc("""A timestamp for when this deposit should become valid
        
This value must overlap with the source allocation. A value of null indicates that the allocation becomes valid
immediately.""")
    val startDate: Long? = null,
    @UCloudApiDoc("""A timestamp for when this deposit should become invalid
        
This value must overlap with the source allocation. A value of null indicates that the allocation will never expire.""")
    val endDate: Long? = null,
)

typealias DepositToWalletResponse = Unit

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class TransferToWalletRequestItem(
    @UCloudApiDoc("The category to transfer from")
    val categoryId: ProductCategoryId,
    @UCloudApiDoc("The target wallet to insert the credits into")
    val target: WalletOwner,
    @UCloudApiDoc("The amount of credits to transfer")
    val amount: Long,
)

typealias TransferToWalletResponse = Unit

@Serializable
data class UpdateAllocationRequestItem(
    val id: String,
    val balance: Long,
    val startDate: Long?,
    val endDate: Long?,
    val reason: String,
)

typealias UpdateAllocationResponse = Unit

object Accounting : CallDescriptionContainer("accounting") {
    const val baseContext = "/api/accounting"

    val charge = call<BulkRequest<ChargeWalletRequestItem>, ChargeWalletResponse, CommonErrorMessage>(
        "charge"
    ) {
        httpUpdate(baseContext, "charge", roles = Roles.SERVICE)
    }

    val deposit = call<BulkRequest<DepositToWalletRequestItem>, DepositToWalletResponse, CommonErrorMessage>(
        "deposit"
    ) {
        httpUpdate(baseContext, "deposit")
    }

    val transfer = call<BulkRequest<TransferToWalletRequestItem>, TransferToWalletResponse, CommonErrorMessage>(
        "transfer"
    ) {
        httpUpdate(baseContext, "transfer")
    }

    val updateAllocation = call<BulkRequest<UpdateAllocationRequestItem>, UpdateAllocationResponse, CommonErrorMessage>(
        "updateAllocation"
    ) {
        httpUpdate(baseContext, "allocation")

        documentation {
            summary = "Update an existing allocation"

            description = """
                Updates one or more existing allocations. This endpoint will use all the provided values. That is,
                you must provide all values, even if they do not change. This will generate a transaction indicating
                the change. This will set the initial balance of the allocation, as if it was initially created with
                this value.
                
                The constraints that are in place during a standard creation are still in place when updating the
                values. This means that the new start and end dates _must_ overlap with the values of all ancestors.
            """.trimIndent()
        }
    }

    val check = call<BulkRequest<ChargeWalletRequestItem>, BulkResponse<Boolean>, CommonErrorMessage>("check") {
        httpUpdate(baseContext, "check", roles = Roles.SERVICE)

        documentation {
            summary = "Checks if one or more wallets are able to carry a charge"
            description = """
                Checks if one or more charges would succeed without lacking credits. This will not generate a
                transaction message, and as a result, the description will never be used.
            """.trimIndent()
        }
    }
}
