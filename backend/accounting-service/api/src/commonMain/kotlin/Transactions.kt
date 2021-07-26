package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.calls.ExperimentalLevel
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiExperimental
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@UCloudApiExperimental(ExperimentalLevel.ALPHA)
@UCloudApiDoc("A transaction log-entry describes a change made to a wallet (allocation)")
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

    @UCloudApiDoc("The `Wallet` which owns the affected allocation")
    abstract val resolvedWallet: Wallet?

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
            The number of products involved in this transaction
            
            For example, for a compute type product, this could be the the number of nodes. The `numberOfProducts`
            property is combined with the `units` property to calculate the final price.
        """)
        val numberOfProducts: Long,
        @UCloudApiDoc("""
            The number of units which this charge pays for
            
            The type of unit is determined by the product itself. See the `unitOfPrice` property of `Product`. This
            could, for example, be the number of hours this product has been in use.
        """)
        val units: Long,

        override val affectedAllocationId: String,
        @UCloudApiDoc(inherit = true, documentation = """
            NOTE: The change for a charge is calculated by combining the
            `pricePerUnit` property of the product (see `productId`) along with the `numberOfProducts` and `units`.
            However, if the target allocation does not have enough credits to cover the full charge, then the `change`
            property will be set to the remaining balance.
        """)
        override val change: Long,
        override val actionPerformedBy: String,
        override val description: String,
        override val timestamp: Long,
        override val resolvedWallet: Wallet?,
        val resolvedProduct: Product?,
    ) : Transaction()

    @Serializable
    @SerialName("deposit")
    data class Deposit(
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
        override val resolvedWallet: Wallet?,
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
        override val resolvedWallet: Wallet?,
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
        override val resolvedWallet: Wallet?,
    ) : Transaction()
}
