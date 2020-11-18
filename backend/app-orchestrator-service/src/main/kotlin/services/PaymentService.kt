package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
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
import kotlin.math.ceil

object MissedPayments : SQLTable("missed_payments") {
    val reservationId = text("reservation_id")
    val amount = long("amount")
    val createdAt = timestamp("created_at")
}

class PaymentService(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient
) {
    sealed class ChargeResult {
        data class Charged(val amountCharged: Long, val pricePerUnit: Long) : ChargeResult()
        object InsufficientFunds : ChargeResult()
        object Duplicate : ChargeResult()
    }

    suspend fun charge(
        job: Job,
        timeUsedInMillis: Long,
        chargeId: String = ""
    ): ChargeResult {
        val parameters = job.parameters ?: error("no parameters")
        val pricePerUnit = job.billing.pricePerUnit

        val units = ceil(timeUsedInMillis / MILLIS_PER_MINUTE.toDouble()).toLong() * parameters.replicas
        val price = pricePerUnit * units
        val result = Wallets.reserveCredits.call(
            ReserveCreditsRequest(
                job.id + chargeId,
                price,
                Time.now(),
                Wallet(
                    job.owner.project ?: job.owner.launchedBy,
                    if (job.owner.project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
                    ProductCategoryId(parameters.product.category, parameters.product.provider),
                ),
                job.owner.launchedBy,
                parameters.product.id,
                units,
                chargeImmediately = true,
                skipIfExists = true
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
            log.error("Failed to charge payment for job: ${job.id} $result")
            db.withSession { session ->
                session.insert(MissedPayments) {
                    set(MissedPayments.reservationId, job.id)
                    set(MissedPayments.amount, price)
                    set(MissedPayments.createdAt, LocalDateTime(Time.now(), DateTimeZone.UTC))
                }
            }
        }

        return ChargeResult.Charged(price, pricePerUnit)
    }

    suspend fun reserve(job: Job) {
        val parameters = job.parameters ?: error("no parameters")
        val pricePerUnit = job.billing.pricePerUnit

        // Note: We reserve credits for a single hour if there is no explicit allocation
        val timeAllocationMillis = parameters.timeAllocation?.toMillis() ?: 3600 * 1000L
        val units = ceil(timeAllocationMillis / MILLIS_PER_MINUTE.toDouble()).toLong() * parameters.replicas
        val price = pricePerUnit * units

        val code = Wallets.reserveCredits.call(
            ReserveCreditsRequest(
                job.id,
                price,
                Time.now() + timeAllocationMillis * 3,
                Wallet(
                    job.owner.project ?: job.owner.launchedBy,
                    if (job.owner.project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
                    ProductCategoryId(parameters.product.category, parameters.product.provider)
                ),
                job.owner.launchedBy,
                parameters.product.id,
                units,
                discardAfterLimitCheck = true
            ),
            serviceClient
        ).statusCode

        when {
            code == HttpStatusCode.PaymentRequired -> {
                throw RPCException(
                    "Insufficient funds for job",
                    HttpStatusCode.PaymentRequired,
                    "NOT_ENOUGH_${ProductArea.COMPUTE}_CREDITS"
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
        private const val MILLIS_PER_MINUTE = 1000L * 60
    }
}
