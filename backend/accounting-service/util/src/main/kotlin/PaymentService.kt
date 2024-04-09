package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable

data class Payment(
    val chargeId: String,
    val periods: Long,
    val units: Long,
    val pricePerUnit: Long,
    val resourceId: String,

    val performedBy: String,
    val owner: WalletOwner,
    val product: ProductReference,
    val description: String? = null,
    val transactionId: String? = null,
)

class PaymentService(
    private val serviceClient: AuthenticatedClient,
) {
    sealed class ChargeResult {
        object Charged : ChargeResult()
        object InsufficientFunds : ChargeResult()
        object Duplicate : ChargeResult()
    }

    suspend fun charge(payments: List<Payment>): List<ChargeResult> {
        TODO()
    }

    suspend fun creditCheckForPayments(payments: List<Payment>): List<ChargeResult> {
        TODO()
    }

    private data class ErrorMessage(val error: String?)
    private val creditCheckCache = AsyncCache<Pair<WalletOwner, ProductCategoryIdV2>, ErrorMessage>(
        BackgroundScope.get(),
        timeToLiveMilliseconds = 60_000,
        timeoutException = {
            throw RPCException("Unable to check allocations for $it", HttpStatusCode.GatewayTimeout)
        },
        retrieve = { (owner, category) ->
            val allWallets = AccountingV2.browseWalletsInternal.call(
                AccountingV2.BrowseWalletsInternal.Request(owner),
                serviceClient
            ).orThrow().wallets

            val isMissingWallet = allWallets.none { it.paysFor.toId() == category }
            if (isMissingWallet) {
                ErrorMessage("You are missing allocations for: $category")
            } else {
                ErrorMessage(null)
            }
        }
    )

    suspend fun creditCheck(
        owner: WalletOwner,
        products: List<ProductReference>
    ) {
        val categories = products.map { ProductCategoryIdV2(it.category, it.provider) }.toSet()
        for (category in categories) {
            val (errorMessage) = creditCheckCache.retrieve(Pair(owner, category))
            if (errorMessage != null) {
                throw RPCException(errorMessage, HttpStatusCode.PaymentRequired)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
