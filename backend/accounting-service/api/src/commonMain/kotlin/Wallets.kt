package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.Roles
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Deprecated("APIs will switch to WalletOwner instead")
enum class WalletOwnerType {
    USER,
    PROJECT
}

@Serializable
data class Wallet(
    val owner: WalletOwner,
    val paysFor: ProductCategoryId,
    val allocations: List<WalletAllocation>,
    val chargePolicy: AllocationSelectorPolicy,
    val productType: ProductType? = null,
    val chargeType: ChargeType? = null,
    val unit: ProductPriceUnit? = null,
)

/*
 * EXPIRE_FIRST takes the wallet allocation with end date closes to now.
 * ORDERED takes the wallet allocation in a user specified order.
 */
@Serializable
enum class AllocationSelectorPolicy {
    EXPIRE_FIRST,
    // ORDERED (Planned not yet implemented)
}

@Serializable
data class WalletAllocation(
    @UCloudApiDoc("A unique ID of this allocation")
    val id: String,
    @UCloudApiDoc(
        """
        A path, starting from the top, through the allocations that will be charged, when a charge is made

        Note that this allocation path will always include, as its last element, this allocation.
    """
    )
    val allocationPath: List<String>,
    @UCloudApiDoc("The current balance of this wallet allocation's subtree")
    val balance: Long,
    @UCloudApiDoc("The initial balance which was granted to this allocation")
    val initialBalance: Long,
    @UCloudApiDoc("The current balance of this wallet allocation")
    val localBalance: Long,
    @UCloudApiDoc("Timestamp for when this allocation becomes valid")
    val startDate: Long,
    @UCloudApiDoc(
        "Timestamp for when this allocation becomes invalid, null indicates that this allocation does not " +
            "expire automatically"
    )
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
    val filterType: ProductType? = null
) : WithPaginationRequestV2

typealias PushWalletChangeResponse = Unit

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A parent allocator's view of a `WalletAllocation`")
data class SubAllocation(
    val id: String,
    val startDate: Long,
    val endDate: Long?,

    val productCategoryId: ProductCategoryId,
    val productType: ProductType,
    val chargeType: ChargeType,
    val unit: ProductPriceUnit,

    val workspaceId: String,
    val workspaceTitle: String,
    val workspaceIsProject: Boolean,

    val remaining: Long,
)

@Serializable
@UCloudApiExperimental(ExperimentalLevel.ALPHA)
data class WalletsBrowseSubAllocationsRequest(
    val sortBy: SortSubAllocationsBy? = null,
    val filterType: ProductType? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

enum class SortSubAllocationsBy {
    GRANT_ALLOCATION,
    PRODUCT_CATEGORY
}

typealias WalletsBrowseSubAllocationsResponse = PageV2<SubAllocation>

@Serializable
data class WalletsRetrieveRecipientRequest(
    val query: String,
)

@Serializable
data class WalletsRetrieveRecipientResponse(
    val id: String,
    val isProject: Boolean,
    val title: String,
    val principalInvestigator: String,
    val numberOfMembers: Int,
)

object Wallets : CallDescriptionContainer("accounting.wallets") {
    const val baseContext = "/api/accounting/wallets"

    val push = call<BulkRequest<PushWalletChangeRequestItem>, PushWalletChangeResponse, CommonErrorMessage>(
        "push"
    ) {
        httpUpdate(baseContext, "push", roles = Roles.SERVICE)
    }

    val browse = call<WalletBrowseRequest, PageV2<Wallet>, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }

    val browseSubAllocations = call<WalletsBrowseSubAllocationsRequest, WalletsBrowseSubAllocationsResponse,
        CommonErrorMessage>("browseSubAllocations") {
        httpBrowse(baseContext, "subAllocation")
    }

    val retrieveRecipient = call<WalletsRetrieveRecipientRequest, WalletsRetrieveRecipientResponse,
        CommonErrorMessage>("retrieveRecipient") {
        httpRetrieve(baseContext, "recipient")
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
    @UCloudApiDoc(
        """
        The number of units that this charge is about
        
        The unit itself is defined by the product. The unit can, for example, describe that the 'units' describe the
        number of minutes/hours/days.
    """
    )
    val units: Long,
    @UCloudApiDoc("The number of products involved in this charge, for example the number of nodes")
    val numberOfProducts: Long,
    @UCloudApiDoc("A reference to the product which the service is charging for")
    val product: ProductReference,
    @UCloudApiDoc("The username of the user who generated this request")
    val performedBy: String,
    @UCloudApiDoc("A description of the charge this is used purely for presentation purposes")
    val description: String
) {
    init {
        checkMinimumValue(this::numberOfProducts, numberOfProducts, 1)
        checkMinimumValue(this::units, units, 1)
    }
}

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
    @UCloudApiDoc(
        """
        A timestamp for when this deposit should become valid
        
        This value must overlap with the source allocation. A value of null indicates that the allocation becomes valid
        immediately.
    """
    )
    val startDate: Long? = null,
    @UCloudApiDoc(
        """
        A timestamp for when this deposit should become invalid
        
        This value must overlap with the source allocation. A value of null indicates that the allocation will never
        expire.
    """
    )
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
    @UCloudApiDoc("The source wallet from where the credits is transferred from")
    val source: WalletOwner,
    @UCloudApiDoc("The amount of credits to transfer")
    val amount: Long,
    @UCloudApiDoc(
        """
        A timestamp for when this deposit should become valid
        
        This value must overlap with the source allocation. A value of null indicates that the allocation becomes valid
        immediately.
    """
    )
    val startDate: Long? = null,
    @UCloudApiDoc(
        """
        A timestamp for when this deposit should become invalid
        
        This value must overlap with the source allocation. A value of null indicates that the allocation will never
        expire.
    """
    )
    val endDate: Long? = null,
)

typealias TransferToWalletResponse = Unit

@Serializable
data class UpdateAllocationRequestItem(
    val id: String,
    val balance: Long,
    val startDate: Long,
    val endDate: Long?,
    val reason: String,
)

typealias UpdateAllocationResponse = Unit

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("See `DepositToWalletRequestItem`")
@Serializable
data class RootDepositRequestItem(
    val categoryId: ProductCategoryId,
    val recipient: WalletOwner,
    val amount: Long,
    val description: String,
    val startDate: Long? = null,
    val endDate: Long? = null
)

object Accounting : CallDescriptionContainer("accounting") {
    const val baseContext = "/api/accounting"

    init {
        serializerLookupTable = mapOf(
            serializerEntry(WalletOwner.User.serializer()),
            serializerEntry(WalletOwner.Project.serializer())
        )
    }

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

    val rootDeposit = call<BulkRequest<RootDepositRequestItem>, Unit, CommonErrorMessage>("rootDeposit") {
        httpUpdate(baseContext, "rootDeposit", roles = Roles.PRIVILEGED)
    }
}
