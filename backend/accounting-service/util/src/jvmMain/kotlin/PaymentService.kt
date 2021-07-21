package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.throwError
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.*
import java.util.*

data class Payment(
    val chargeId: String,
    val units: Long,
    val pricePerUnit: Long,
    val resourceId: String,

    val launchedBy: String,
    val project: String?,
    val product: ProductReference,
    val productArea: ProductArea,
)

val Payment.wallet: Wallet
    get() = TODO()/*Wallet(
        project ?: launchedBy,
        if (project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
        ProductCategoryId(product.category, product.provider)
    )*/


class PaymentService(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
) {
    sealed class ChargeResult {
        data class Charged(val amountCharged: Long, val pricePerUnit: Long) : ChargeResult()
        object InsufficientFunds : ChargeResult()
        object Duplicate : ChargeResult()
    }

    suspend fun charge(payment: Payment): ChargeResult {
        TODO()
        /*
        with(payment) {
            val price = pricePerUnit * units
            val result = Wallets.reserveCredits.call(
                ReserveCreditsRequest(
                    resourceId + chargeId,
                    price,
                    Time.now(),
                    wallet,
                    launchedBy,
                    product.id,
                    units,
                    chargeImmediately = true,
                    skipIfExists = false,
                    transactionType = TransactionType.PAYMENT,
                ),
                serviceClient
            )

            if (result is IngoingCallResponse.Error<*, *>) {
                if (result.statusCode == HttpStatusCode.PaymentRequired) {
                    return ChargeResult.InsufficientFunds
                }
                if (result.statusCode == HttpStatusCode.Conflict) {
                    return ChargeResult.Duplicate
                }

                result.throwError()
            }

            return ChargeResult.Charged(price, pricePerUnit)
        }
         */
    }

    suspend fun reserve(payment: Payment, expiresIn: Long = 1000L * 60 * 60) {
        TODO()
        /*
        with(payment) {
            val price = pricePerUnit * units

            val code = Wallets.reserveCredits.call(
                ReserveCreditsRequest(
                    resourceId,
                    price,
                    Time.now() + expiresIn,
                    wallet,
                    launchedBy,
                    product.id,
                    units,
                    discardAfterLimitCheck = true,
                    transactionType = TransactionType.PAYMENT,
                ),
                serviceClient
            ).statusCode

            when {
                code == HttpStatusCode.PaymentRequired -> {
                    throw RPCException(
                        "Insufficient funds for job",
                        HttpStatusCode.PaymentRequired,
                        "NOT_ENOUGH_${payment.productArea}_CREDITS"
                    )
                }

                code.isSuccess() -> {
                    // Do nothing
                }

                else -> throw RPCException.fromStatusCode(code)
            }
        }
         */
    }

    suspend fun creditCheck(
        product: Product,
        accountId: String,
        accountType: WalletOwnerType,
    ) {
        TODO()
        /*
        val code = Wallets.reserveCredits.call(
            ReserveCreditsRequest(
                UUID.randomUUID().toString(),
                1L,
                System.currentTimeMillis(),
                Wallet(accountId, accountType, product.category),
                "_ucloud",
                product.id,
                0L,
                discardAfterLimitCheck = true,
                transactionType = TransactionType.PAYMENT
            ),
            serviceClient
        ).statusCode

        when {
            code == HttpStatusCode.PaymentRequired -> {
                throw RPCException(
                    "Insufficient funds for job",
                    HttpStatusCode.PaymentRequired,
                    "NOT_ENOUGH_${product.area}_CREDITS"
                )
            }

            code.isSuccess() -> {
                // Do nothing
            }

            else -> throw RPCException.fromStatusCode(code)
        }
         */
    }

    companion object : Loggable {
        override val log = logger()
    }
}