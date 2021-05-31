package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.app.orchestrator.api.Ingress
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.License
import dk.sdu.cloud.app.orchestrator.api.NetworkIP
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*
import kotlin.math.ceil

object MissedPayments : SQLTable("missed_payments") {
    val reservationId = text("reservation_id")
    val amount = long("amount")
    val createdAt = timestamp("created_at")
    val type = text("type")
}

sealed class Payment {
    abstract val chargeId: String
    abstract val units: Long
    abstract val pricePerUnit: Long
    abstract val type: String
    abstract val resourceId: String

    abstract val launchedBy: String
    abstract val project: String?
    abstract val product: ProductReference
    abstract val productArea: ProductArea

    data class OfJob(val job: Job, val timeUsedInMillis: Long, override val chargeId: String) : Payment() {
        override val type = "job"
        override val resourceId = job.id

        override val pricePerUnit = job.billing.pricePerUnit
        override val units = ceil(timeUsedInMillis / MILLIS_PER_MINUTE.toDouble()).toLong() * job.specification.replicas

        override val product = job.specification.product
        override val launchedBy: String = job.owner.createdBy
        override val project: String? = job.owner.project
        override val productArea = ProductArea.COMPUTE

        companion object {
            private const val MILLIS_PER_MINUTE = 1000L * 60
        }
    }

    data class OfIngress(val ingress: Ingress, override val units: Long, override val chargeId: String) : Payment() {
        override val type = "ingress"
        override val resourceId = ingress.id

        override val pricePerUnit = ingress.billing.pricePerUnit

        override val product = ingress.specification.product
        override val launchedBy: String = ingress.owner.createdBy
        override val project: String? = ingress.owner.project
        override val productArea = ProductArea.INGRESS
    }

    data class OfLicense(val license: License, override val units: Long, override val chargeId: String) : Payment() {
        override val type = "license"
        override val resourceId = license.id

        override val pricePerUnit = license.billing.pricePerUnit

        override val product = license.specification.product
        override val launchedBy: String = license.owner.createdBy
        override val project: String? = license.owner.project
        override val productArea = ProductArea.LICENSE
    }

    data class OfNetworkIP(
        val networkIp: NetworkIP,
        override val units: Long,
        override val chargeId: String,
    ) : Payment() {
        override val type = "network_ip"
        override val resourceId = networkIp.id

        override val pricePerUnit = networkIp.billing.pricePerUnit

        override val product = networkIp.specification.product
        override val launchedBy: String = networkIp.owner.createdBy
        override val project: String? = networkIp.owner.project
        override val productArea = ProductArea.NETWORK_IP
    }
}

val Payment.wallet: Wallet
    get() = Wallet(
        project ?: launchedBy,
        if (project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
        ProductCategoryId(product.category, product.provider)
    )

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

            if (result is IngoingCallResponse.Error) {
                if (result.statusCode == HttpStatusCode.PaymentRequired) {
                    return ChargeResult.InsufficientFunds
                }
                if (result.statusCode == HttpStatusCode.Conflict) {
                    return ChargeResult.Duplicate
                }
                log.error("Failed to charge payment for $type: $resourceId $result")
                db.withSession { session ->
                    session.insert(MissedPayments) {
                        set(MissedPayments.reservationId, resourceId)
                        set(MissedPayments.amount, price)
                        set(MissedPayments.type, type)
                        set(MissedPayments.createdAt, LocalDateTime(Time.now(), DateTimeZone.UTC))
                    }
                }
            }

            return ChargeResult.Charged(price, pricePerUnit)
        }
    }

    suspend fun reserve(payment: Payment, expiresIn: Long = 1000L * 60 * 60) {
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
    }

    suspend fun creditCheck(
        product: Product,
        accountId: String,
        accountType: WalletOwnerType,
    ) {
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
    }

    companion object : Loggable {
        override val log = logger()
    }
}
