package dk.sdu.cloud.accounting.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Transaction {
    abstract val units: Long
    abstract val actionPerformedBy: String
    abstract val product: Product
    abstract val targetWallet: Wallet
    abstract val description: String?
    abstract val numberOfProducts: Long

    @Serializable
    @SerialName("deposit")
    data class Deposit(
        override val units: Long,
        override val actionPerformedBy: String,
        override val product: Product,
        override val targetWallet: Wallet,
        override val description: String,
        override val numberOfProducts: Long = 1
    ) : Transaction() {
        init {
            require(units > 0)
            require(numberOfProducts == 1L)
        }
    }

    @Serializable
    @SerialName("charge")
    data class Charge(
        override val units: Long,
        override val actionPerformedBy: String,
        override val product: Product,
        override val targetWallet: Wallet,
        override val description: String,
        override val numberOfProducts: Long,
    ) : Transaction() {
        init {
            require(numberOfProducts > 0)

        }
    }

    @Serializable
    @SerialName("transfer")
    data class Transfer (
        override val units: Long,
        override val actionPerformedBy: String,
        override val product: Product,
        override val targetWallet: Wallet,
        override val description: String,
        override val numberOfProducts: Long,
        val actionPerformedByWallet: String,
        val transferFromWallet: Wallet
    ) : Transaction() {
        init {
            require(units > 0)
            require(targetWallet.owner != transferFromWallet.owner)
        }
    }
}
