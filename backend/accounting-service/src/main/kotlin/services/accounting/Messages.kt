package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.util.IdCard
import kotlinx.serialization.json.JsonObject

sealed class AccountingRequest<Resp> {
    abstract val idCard: IdCard

    data class RootAllocate(
        override val idCard: IdCard,
        val category: ProductCategoryIdV2,
        val amount: Long,
        val start: Long,
        val end: Long,
    ) : AccountingRequest<Int>()

    data class SubAllocate(
        override val idCard: IdCard,
        val category: ProductCategoryIdV2,
        val owner: String,
        val quota: Long,
        val start: Long,
        val end: Long,
        val ownerOverride: String? = null,
        val grantedIn: Long? = null,
    ) : AccountingRequest<Int>()

    data class RollBackGrantAllocations(
        override val idCard: IdCard,
        val grantedIn: Long? = null,
    ) : AccountingRequest<Int>()

    data class CommitAllocations(
        override val idCard: IdCard,
        val grantedIn: Long? = null,
        val ids: List<Int>? = null
    ) : AccountingRequest<Int>()

    data class Charge(
        override val idCard: IdCard,
        val owner: String,
        val category: ProductCategoryIdV2,
        val amount: Long,
        val isDelta: Boolean,

        // If the scope is not null, then this will tell the system that this is a charge of a specific resource (of
        // some kind). The accounting system will internally keep track of the usage for each scope. This could, for
        // example, be a single job or drive. However, there are no strict requirements on what a scope can be. A
        // scope must be considered opaque from the point-of-view of UCloud/Core. Delta and absolute charges work only
        // within a single scope. This way, a provider can perform periodic charges on a job and finish of with a single
        // charge which performs "correction".
        //
        // The scope must be unique for a single (owner, productCategoryId, scope) tuple.
        val scope: String? = null,

        // The scope explanation is attached to the charge scope. The most recent non-null scope explanation is used.
        // That is, the provider is not required to send one for every charge.
        val scopeExplanation: String? = null,
    ) : AccountingRequest<Unit>()

    data class SystemCharge(
        override val idCard: IdCard = IdCard.System,
        val amount: Long,
        val walletId: Long,
        val isDelta: Boolean
    ) : AccountingRequest<Unit>()

    data class ScanRetirement(
        override val idCard: IdCard
    ) : AccountingRequest<Unit>()

    data class MaxUsable(
        override val idCard: IdCard,
        val category: ProductCategoryIdV2,
    ) : AccountingRequest<Long>()

    data class StopSystem(
        override val idCard: IdCard
    ) : AccountingRequest<Unit>()

    data class BrowseWallets(
        override val idCard: IdCard,
        val includeChildren: Boolean = false,
        val childQuery: String? = null,
        val filterProductType: ProductType? = null,
    ) : AccountingRequest<List<WalletV2>>()

    data class UpdateAllocation(
        override val idCard: IdCard,
        val allocationId: Int,
        val newQuota: Long? = null,
        val newStart: Long? = null,
        val newEnd: Long? = null,
    ) : AccountingRequest<Unit>()

    data class RetrieveProviderAllocations(
        override val idCard: IdCard,
        override val itemsPerPage: Int? = null,
        override val next: String? = null,
        override val consistency: PaginationRequestV2Consistency? = null,
        override val itemsToSkip: Long? = null,

        val filterOwnerId: String? = null,
        val filterOwnerIsProject: Boolean? = null,
        val filterCategory: String? = null,
    ) : AccountingRequest<PageV2<AccountingV2.BrowseProviderAllocations.ResponseItem>>(), WithPaginationRequestV2

    data class FindRelevantProviders(
        override val idCard: IdCard,
        val username: String,
        val project: String?,
        val useProject: Boolean,
        val filterProductType: ProductType? = null,
    ) : AccountingRequest<Set<String>>()

    data class ProviderCheckUsable(
        override val idCard: IdCard,
        val category: ProductCategoryIdV2,
        val owner: String,
    ) : AccountingRequest<Long>()

    data class RetrieveScopedUsage(
        override val idCard: IdCard,
        val owner: WalletOwner,
        val chargeId: String
    ) : AccountingRequest<Long>()

    data class RetrieveDescendants(
        override val idCard: IdCard,
        val projectId: String
    ) : AccountingRequest<List<String>>()

    // NOTE(Dan): Please do not do anything stupid with this API. You should probably only ever do
    // reads inside the handler. It should not be anything slow since it can stall the entire accounting system if
    // it is.
    data class ForEachUpdatedWallet(
        override val idCard: IdCard,
        val providerId: String,
        val since: Long,
        val handler: suspend (InternalWallet) -> Unit,
    ) : AccountingRequest<Unit>()

    data class DebugState(
        override val idCard: IdCard,
        val roots: List<Int>? = null,
    ) : AccountingRequest<String>()

    data class DebugWallet(
        override val idCard: IdCard,
        val walletId: Int
    ) : AccountingRequest<JsonObject>()

    data class DebugCharge(
        override val idCard: IdCard,
        val walletId: Int,
        val charge: Long,
    ) : AccountingRequest<Unit>()

    data class DebugUsable(
        override val idCard: IdCard,
    ) : AccountingRequest<Unit>()
}

