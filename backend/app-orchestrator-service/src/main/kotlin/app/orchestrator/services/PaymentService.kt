package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
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
    data class ChargeResult(val amountCharged: Long, val pricePerUnit: Long)

    suspend fun charge(
        job: VerifiedJob,
        timeUsedInMillis: Long
    ): ChargeResult {
        val pricePerUnit = job.reservation.pricePerUnit

        val units = ceil(timeUsedInMillis / MILLIS_PER_MINUTE.toDouble()).toLong() * job.nodes
        val price = pricePerUnit * units
        val result = Wallets.chargeReservation.call(
            ChargeReservationRequest(
                job.id,
                price,
                units
            ),
            serviceClient
        )

        if (result is IngoingCallResponse.Error) {
            log.error("Failed to charge payment for job: ${job.id} $result")
            db.withSession { session ->
                session.insert(MissedPayments) {
                    set(MissedPayments.reservationId, job.id)
                    set(MissedPayments.amount, price)
                    set(MissedPayments.createdAt, LocalDateTime(Time.now(), DateTimeZone.UTC))
                }
            }
        }

        return ChargeResult(price, pricePerUnit)
    }

    suspend fun reserve(job: VerifiedJob) {
        val pricePerUnit = job.reservation.pricePerUnit
        val units = ceil(job.maxTime.toMillis() / MILLIS_PER_MINUTE.toDouble()).toLong() * job.nodes
        val price = pricePerUnit * units

        val code = Wallets.reserveCredits.call(
            ReserveCreditsRequest(
                job.id,
                price,
                Time.now() + job.maxTime.toMillis() * 3,
                Wallet(
                    job.project ?: job.owner,
                    if (job.project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
                    job.reservation.category
                ),
                job.owner,
                job.reservation.id,
                units,
                transactionType = TransactionType.PAYMENT
            ),
            serviceClient
        ).statusCode

        when {
            code == HttpStatusCode.PaymentRequired -> {
                throw RPCException(
                    "Insufficient funds for job",
                    HttpStatusCode.PaymentRequired,
                    "NOT_ENOUGH_${job.reservation.area.name}_CREDITS"
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
