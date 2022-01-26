package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.*
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
        return Accounting.charge.call(
            BulkRequest(payments.map {
                ChargeWalletRequestItem(
                    it.owner,
                    it.units,
                    it.periods,
                    it.product,
                    it.performedBy,
                    it.description ?: "Payment",
                    "${it.product.provider}-${it.chargeId}"
                )
            }),
            serviceClient
        ).orThrow().responses.map { success ->
            if (success) ChargeResult.Charged
            else ChargeResult.InsufficientFunds
        }
    }

    suspend fun creditCheckForPayments(payments: List<Payment>): List<ChargeResult> {
        return Accounting.check.call(
            BulkRequest(payments.map {
                ChargeWalletRequestItem(
                    it.owner,
                    it.units,
                    it.periods,
                    it.product,
                    it.performedBy,
                    it.description ?: "Payment",
                    "${it.product.provider}-${it.chargeId}"
                )
            }),
            serviceClient
        ).orThrow().responses.map { success ->
            if (success) ChargeResult.Charged
            else ChargeResult.InsufficientFunds
        }
    }

    suspend fun creditCheck(
        owner: WalletOwner,
        products: List<ProductReference>
    ) {
        val success = Accounting.check.call(
            BulkRequest(
                products.map { product ->
                    ChargeWalletRequestItem(
                        owner,
                        1L, 1L,
                        product,
                        "_ucloud",
                        "Credit check",
                        "${product.provider}-${UUID.randomUUID()}"
                    )
                }
            ),
            serviceClient
        ).orThrow().responses.all { it }

        if (!success) {
            throw RPCException(
                "Insufficient funds",
                HttpStatusCode.PaymentRequired
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
