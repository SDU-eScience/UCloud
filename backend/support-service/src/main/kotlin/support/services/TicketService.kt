package dk.sdu.cloud.support.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.slack.api.SendSupportRequest
import dk.sdu.cloud.slack.api.SlackDescriptions
import dk.sdu.cloud.slack.api.Ticket
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class TicketService(private val authenticatedClient: AuthenticatedClient) {
    suspend fun createTicket(ticket: Ticket) {
        SlackDescriptions.sendSupport.call(
            SendSupportRequest(
                ticket.requestId,
                ticket.principal,
                ticket.userAgent,
                ticket.message
            ),
            authenticatedClient
        ).orThrow()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
