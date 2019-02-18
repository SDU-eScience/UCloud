package dk.sdu.cloud.support.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.lang.IllegalArgumentException


internal val ticket = Ticket(
    "ID",
    SecurityPrincipal("username", Role.USER, "first", "last", 123456),
    "userAgent",
    "This is the message")

class TicketServiceTest{

    private val slack = mockk<SlackNotifier>()

    @Test
    fun `test create`() {
        coEvery { slack.onTicket(any()) } just Runs
        val ticketService = TicketService(listOf(slack))

        runBlocking {
            ticketService.createTicket(ticket)
        }
    }

    @Test (expected = RPCException::class)
    fun `test create - failure`() {
        coEvery { slack.onTicket(any()) } throws RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        val ticketService = TicketService(listOf(slack))

        runBlocking {
            ticketService.createTicket(ticket)
        }
    }

    @Test (expected = IllegalArgumentException::class)
    fun `test create - failure - empty notifier list`() {
        val ticketService = TicketService(emptyList())
    }
}
