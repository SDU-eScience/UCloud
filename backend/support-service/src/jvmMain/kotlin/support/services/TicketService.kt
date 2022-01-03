package dk.sdu.cloud.support.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.mail.api.MailDescriptions
import dk.sdu.cloud.mail.api.SendSupportEmailRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.slack.api.SendSupportRequest
import dk.sdu.cloud.slack.api.SlackDescriptions
import dk.sdu.cloud.slack.api.Ticket

class TicketService(private val authenticatedClient: AuthenticatedClient) {
    suspend fun createTicket(ticket: Ticket) {
        SlackDescriptions.sendSupport.call(
            SendSupportRequest(
                ticket.requestId,
                ticket.principal,
                ticket.userAgent,
                ticket.subject,
                ticket.message
            ),
            authenticatedClient
        ).orThrow()
        MailDescriptions.sendSupport.call(
            SendSupportEmailRequest(
                ticket.principal.email ?: "",
                ticket.subject,
                ticket.message
            ),
            authenticatedClient
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
