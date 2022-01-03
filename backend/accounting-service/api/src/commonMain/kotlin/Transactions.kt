package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.PaginationRequestV2Consistency
import dk.sdu.cloud.WithPaginationRequestV2
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A transaction log-entry describes a change made to a wallet (allocation)")
@Serializable
sealed class Transaction {
    @UCloudApiDoc("""
        A change in balance of the affected allocation

        A positive value indicates an increase in the allocation's balance and a decrease indicates a decrease in the
        balance. NOTE: This is also the case for differential products, which will show the change since the last
        transaction was made. For example, a differential product might show an increase in 5GB since the previous
        transaction.
    """)
    abstract val change: Long
    @UCloudApiDoc("""
        The username of the user who performed this action

        UCloud reserves the right to perform some internal transaction changes. These will be performed by a service
        account, which is indicated by a username prefix of '_'. For example, `"_ucloud"`.
    """)
    abstract val actionPerformedBy: String
    @UCloudApiDoc("A human readable description of why this change was made")
    abstract val description: String
    @UCloudApiDoc("The ID of the `WalletAllocation` which was affected")
    abstract val affectedAllocationId: String
    @UCloudApiDoc("A timestamp indicating when this change was made")
    abstract val timestamp: Long

    @UCloudApiDoc("The product category which this transaction belongs to")
    abstract val resolvedCategory: ProductCategoryId

    @UCloudApiDoc("ID of the transaction that was received from provider or user. Used for traceability and duplicate prevention")
    abstract val initialTransactionId: String

    @UCloudApiDoc("An UUID to a transactions. When the transaction is the root transaction then initial transaction id and transaction id is the same")
    abstract val transactionId: String

    @Serializable
    @SerialName("charge")
    data class Charge(
        @UCloudApiDoc("""
            The source allocation ID that this change is originating from

            NOTE: This can be the same as the `affectedAllocationId`.
        """)
        val sourceAllocationId: String,
        @UCloudApiDoc("""
            The ID of the product which was charged for

            NOTE: This should be combined with the `resolvedWallet`'s `paysFor` attribute to create a complete product
            reference.
        """)
        val productId: String,
        @UCloudApiDoc("""
            The number of periods for this transaction.
            
            For example, for a compute type product, this could be the the number of nodes. The `periods`
            property is combined with the `units` property to calculate the final price.
        """)
        val periods: Long,
        @UCloudApiDoc("""
            The number of units which this charge pays for
            
            The type of unit is determined by the product itself. See the `unitOfPrice` property of `Product`. This
            could, for example, be the number of hours this product has been in use.
        """)
        val units: Long,

        override val affectedAllocationId: String,
        @UCloudApiDoc(inherit = true, documentation = """
            NOTE: The change for a charge is calculated by combining the
            `pricePerUnit` property of the product (see `productId`) along with the `periods` and `units`.
            However, if the target allocation does not have enough credits to cover the full charge, then the `change`
            property will be set to the remaining balance.
        """)
        override val change: Long,
        override val actionPerformedBy: String,
        override val description: String,
        override val timestamp: Long,
        override val resolvedCategory: ProductCategoryId,
        override val initialTransactionId: String,
        override val transactionId: String
    ) : Transaction()

    @Serializable
    @SerialName("deposit")
    data class Deposit(
        @UCloudApiDoc("The source allocation which the affected allocation is created from")
        val sourceAllocationId: String?,
        @UCloudApiDoc("Timestamp for when the affected allocation becomes valid (`null` indicates none)")
        val startDate: Long?,
        @UCloudApiDoc("Timestamp for when the affected allocation no longer is valid (`null` indicates none)")
        val endDate: Long?,

        override val change: Long,
        override val actionPerformedBy: String,
        override val description: String,
        override val affectedAllocationId: String,
        override val timestamp: Long,
        override val resolvedCategory: ProductCategoryId,
        override val initialTransactionId: String,
        override val transactionId: String
    ) : Transaction()

    @Serializable
    @SerialName("transfer")
    data class Transfer(
        @UCloudApiDoc("The source allocation which the affected allocation is created from")
        val sourceAllocationId: String,
        @UCloudApiDoc("Timestamp for when the affected allocation becomes valid (`null` indicates none)")
        val startDate: Long?,
        @UCloudApiDoc("Timestamp for when the affected allocation no longer is valid (`null` indicates none)")
        val endDate: Long?,

        override val change: Long,
        override val actionPerformedBy: String,
        override val description: String,
        override val affectedAllocationId: String,
        override val timestamp: Long,
        override val resolvedCategory: ProductCategoryId,
        override val initialTransactionId: String,
        override val transactionId: String
    ) : Transaction()

    @Serializable
    @SerialName("allocation_update")
    data class AllocationUpdate(
        @UCloudApiDoc("Timestamp for when the affected allocation becomes valid (`null` indicates none)")
        val startDate: Long?,
        @UCloudApiDoc("Timestamp for when the affected allocation no longer is valid (`null` indicates none)")
        val endDate: Long?,

        override val change: Long,
        override val actionPerformedBy: String,
        override val description: String,
        override val affectedAllocationId: String,
        override val timestamp: Long,
        override val resolvedCategory: ProductCategoryId,
        override val initialTransactionId: String,
        override val transactionId: String
    ) : Transaction()
}

@Serializable
data class TransactionsBrowseRequest(
    val filterCategory: String? = null,
    val filterProvider: String? = null,
    override val itemsPerPage: Int? = null,
    override val next: String? = null,
    override val consistency: PaginationRequestV2Consistency? = null,
    override val itemsToSkip: Long? = null,
) : WithPaginationRequestV2

object Transactions : CallDescriptionContainer("accounting.transactions") {
    const val baseContext = "/api/accounting/transactions"

    init {
        serializerLookupTable = mapOf(
            serializerEntry(Transaction.Deposit.serializer()),
            serializerEntry(Transaction.AllocationUpdate.serializer()),
            serializerEntry(Transaction.Transfer.serializer()),
            serializerEntry(Transaction.Charge.serializer()),
        )
    }

    val browse = call<TransactionsBrowseRequest, PageV2<Transaction>, CommonErrorMessage>("browse") {
        httpBrowse(baseContext)
    }
}
