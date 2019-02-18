package dk.sdu.cloud.support.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.jobId
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.support.api.SupportDescriptions
import dk.sdu.cloud.support.services.Ticket
import dk.sdu.cloud.support.services.TicketService
import dk.sdu.cloud.support.util.LocalRateLimiter
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.userAgent

class SupportController(
    private val ticketService: TicketService
) : Controller {
    private val ticketLimiter = LocalRateLimiter(1000L * 60 * 60, 10)

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(SupportDescriptions.createTicket) {
            with(ctx as HttpCall) {
                if (!ticketLimiter.checkAndTrack(ctx.securityPrincipal)) {
                    error(CommonErrorMessage("Too many requests"), HttpStatusCode.TooManyRequests)
                    return@implement
                }

                if (request.message.length > 1024 * 512) {
                    error(CommonErrorMessage("Too large"), HttpStatusCode.PayloadTooLarge)
                    return@implement
                }

                val ticket = Ticket(
                    ctx.jobId,
                    ctx.securityPrincipal,
                    call.request.userAgent() ?: "Missing UA",
                    request.message
                )

                ticketService.createTicket(ticket)
                ok(Unit)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
