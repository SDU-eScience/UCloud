package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendBulkRequest
import dk.sdu.cloud.mail.api.SendRequest
import dk.sdu.cloud.project.api.LookupAdminsRequest
import dk.sdu.cloud.project.api.LookupAdminsResponse
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.project.api.ViewMemberInProjectRequest
import dk.sdu.cloud.project.api.ViewProjectRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.escapeHtml
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
                    set(MissedPayments.createdAt, LocalDateTime.now(DateTimeZone.UTC))
                }
            }
        }
    }

    suspend fun reserve(job: VerifiedJob) {
        val pricePerUnit = job.reservation.pricePerUnit
        val units = ceil(job.maxTime.toMillis() / MILLIS_PER_MINUTE.toDouble()).toLong() * job.nodes
        val price = pricePerUnit * units

        val code = Wallets.reserveCredits.call(
            ReserveCreditsRequest(
                job.id,
                price,
                System.currentTimeMillis() + job.maxTime.toMillis() * 3,
                Wallet(
                    job.project ?: job.owner,
                    if (job.project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
                    job.reservation.category
                ),
                job.owner,
                job.reservation.id,
                units
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

        //Check Wallet status and send email to admins and parent admins if low on funds
        val balance = Wallets.retrieveBalance.call(
            RetrieveBalanceRequest(
                job.project ?: job.owner,
                if (job.project != null) WalletOwnerType.PROJECT else WalletOwnerType.USER,
                false
            ),
            serviceClient
        ).orThrow()

        val getAdmins = if (job.project != null) {
            val projectAdmins = ProjectMembers.lookupAdmins.call(
                LookupAdminsRequest(job.project!!),
                serviceClient
            ).orThrow()
            val project = Projects.viewProject.call(
                ViewProjectRequest(job.project!!),
                serviceClient
            ).orThrow()
            val parentProjectAdmins = if (project.parent != null) {
                ProjectMembers.lookupAdmins.call(
                    LookupAdminsRequest(project.parent!!),
                    serviceClient
                ).orThrow()
            } else {
                LookupAdminsResponse(emptyList())
            }
            LookupAdminsResponse((projectAdmins.admins + parentProjectAdmins.admins).toSet().toList())
        } else {
            LookupAdminsResponse(listOf(ProjectMember(job.owner, ProjectRole.PI)))
        }

        val projectTitle = if(job.project != null) {
            Projects.viewProject.call(
                ViewProjectRequest(job.project ?: ""),
                serviceClient
            ).orThrow().title
        } else {
            "personal project"
        }

        balance.wallets.forEach { wallet ->
            if (wallet.area == job.reservation.area) {
                if (wallet.balance < CREDITS_NOTIFY_LIMIT && !wallet.wallet.lowFundsEmailSend) {
                    val messages = getAdmins.admins.map { admin ->
                        SendRequest(
                            admin.username,
                            "Low resources for project",
                            lowResourcesTemplate(admin.username, wallet.area, projectTitle)
                        )
                    }
                    MailDescriptions.sendBulk.call(
                        SendBulkRequest(messages),
                        serviceClient
                    )
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
        private const val MILLIS_PER_MINUTE = 1000L * 60
        private const val CREDITS_NOTIFY_LIMIT = 5000000
    }

    fun lowResourcesTemplate(
        recipient: String,
        productArea: ProductArea,
        projectTitle: String
    ) = """
    <p>Dear ${escapeHtml(recipient)}</p>
    <p>
        We write to you to inform you that the project: ${escapeHtml(projectTitle)} is running low on the 
        ${escapeHtml(productArea.name)} resource.
    </p>
    <p>If you do not want to receive these notifications per mail, 
    you can unsubscribe to non-crucial emails in your personal settings on UCloud</p>
    """".trimIndent()
}
