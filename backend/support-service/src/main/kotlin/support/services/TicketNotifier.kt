package dk.sdu.cloud.support.services

interface TicketNotifier {
    suspend fun onTicket(ticket: Ticket)
}
