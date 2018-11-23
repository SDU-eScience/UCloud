package dk.sdu.cloud.support.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.support.api.SupportDescriptions
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.safeJobId
import dk.sdu.cloud.service.securityPrincipal
import dk.sdu.cloud.support.services.Ticket
import dk.sdu.cloud.support.services.TicketService
import dk.sdu.cloud.support.util.LocalRateLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.request.userAgent
import io.ktor.routing.Route
import java.util.*

class SupportController(
    private val ticketService: TicketService
) : Controller {
    override val baseContext = SupportDescriptions.baseContext
    private val ticketLimiter = LocalRateLimiter(1000L * 60 * 60, 10)

    override fun configure(routing: Route): Unit = with(routing) {
        implement(SupportDescriptions.createTicket) { req ->
            if (!ticketLimiter.checkAndTrack(call.securityPrincipal)) {
                error(CommonErrorMessage("Too many requests"), HttpStatusCode.TooManyRequests)
                return@implement
            }

            if (req.message.length > 1024 * 512) {
                error(CommonErrorMessage("Too large"), HttpStatusCode.PayloadTooLarge)
                return@implement
            }

            val ticket = Ticket(
                call.request.safeJobId ?: "missing-${UUID.randomUUID()}",
                call.securityPrincipal,
                call.request.userAgent() ?: "Missing UA",
                req.message
            )

            ticketService.createTicket(ticket)
            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
