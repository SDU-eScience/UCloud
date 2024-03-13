package dk.sdu.cloud.accounting.services.accounting

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.ProductCategoryIdV2
import dk.sdu.cloud.accounting.util.IdCard

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
        val parentWallet: Int,
        val owner: String,
        val quota: Long,
        val start: Long,
        val end: Long,
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
}

