package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import java.util.*

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
    private val db: DBContext,
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

    suspend fun creditCheck(
        owner: WalletOwner,
        products: List<ProductReference>
    ) {
        TODO()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
