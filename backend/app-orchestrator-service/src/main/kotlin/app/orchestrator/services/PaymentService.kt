package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.compute.api.AccountType
import dk.sdu.cloud.accounting.compute.api.AccountingCompute
import dk.sdu.cloud.accounting.compute.api.ChargeReservationRequest
import dk.sdu.cloud.accounting.compute.api.ReserveCreditsRequest
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
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
    suspend fun charge(
        job: VerifiedJob,
        timeUsedInMillis: Long
    ) {
        val pricePerHour = job.reservation.pricePerHour ?: run {
            log.error("No price attached with job! $job")
            0L
        }

        val price = ceil(pricePerHour * (timeUsedInMillis / MILLIS_PER_HOUR.toDouble())).toLong()
        val result = AccountingCompute.chargeReservation.call(
            ChargeReservationRequest(
                job.id,
                price
            ),
            serviceClient
        )

        if (result is IngoingCallResponse.Error) {
            log.error("Failed to charge payment for job: ${job.id} $result")
            db.withSession { session ->
                session.insert(MissedPayments) {
                    set(MissedPayments.reservationId, job.id)
                    set(MissedPayments.amount, price)
                    set(MissedPayments.createdAt, LocalDateTime.now(DateTimeZone.UTC))
                }
            }
        }
    }

    suspend fun reserve(job: VerifiedJob) {
        val pricePerHour = job.reservation.pricePerHour
            ?: throw IllegalStateException("Machine has no price associated with it!")

        val price = ceil(pricePerHour * (job.maxTime.toMillis() / MILLIS_PER_HOUR.toDouble())).toLong()

        val code = AccountingCompute.reserveCredits.call(
            ReserveCreditsRequest(
                job.id,
                price,
                job.maxTime.toMillis() * 3,
                job.project ?: job.owner,
                if (job.project != null) AccountType.PROJECT else AccountType.USER,
                job.owner
            ),
            serviceClient
        ).statusCode

        when {
            code == HttpStatusCode.PaymentRequired -> {
                throw RPCException("Insufficient funds for job", HttpStatusCode.PaymentRequired)
            }

            code.isSuccess() -> {
                // Do nothing
            }

            else -> throw RPCException.fromStatusCode(code)
        }
    }

    companion object : Loggable {
        override val log = logger()
        private const val MILLIS_PER_HOUR = 1000L * 3600
    }
}
