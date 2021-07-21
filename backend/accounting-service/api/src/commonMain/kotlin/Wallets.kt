package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
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
    val id: String,
    val type: WalletOwnerType,
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
    val id: String,
    val associatedWallet: Wallet,
    val parent: Wallet,
    val balance: Long,
    val initialBalance: Long,
    val startDate: Long,
    val endDate: Long?
)

@Serializable
data class PushWalletChangeRequestItem(
    val walletOwner: String, //e.g. Username or ProjectId
    val amount: Long,
    val productId: String
)

// Browse via the pagination v2 API
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
data class ChargeWalletRequestItem(
    val payerId: String, //Username or projectId
    val units: Long, //E.g. duration (storage and compute)
    val numberOfProducts: Long, // e.g. number of GB/number of nodes/number of instances (IPs, links, licenses)
    val productId: String
)

typealias ChargeWalletResponse = Unit

@Serializable
data class DepositToWalletRequestItem(
    val receiverId: String, //Username or projectId
    val amount: Long,
    val productId: String
)

typealias DepositToWalletResponse = Unit

@Serializable
data class TransferToWalletRequestItem(
    val fromWalletId: String,
    val toWalletId: String,
    val amount: Long,
)

typealias TransferToWalletResponse = Unit

object Accounting : CallDescriptionContainer("accounting") {
    val baseContext = "/api/accounting"

    val charge = call<BulkRequest<ChargeWalletRequestItem>, ChargeWalletResponse, CommonErrorMessage>(
        "charge"
    ) {
        httpUpdate(baseContext, "charge")
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
}
