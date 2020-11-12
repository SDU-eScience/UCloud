package dk.sdu.cloud.support.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.slack.api.SlackDescriptions
import dk.sdu.cloud.slack.api.Ticket
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.coEvery
import kotlinx.coroutines.runBlocking
import org.junit.Test


internal val ticket = Ticket(
    "ID",
    SecurityPrincipal("username", Role.USER, "first", "last", 123456),
    "userAgent",
    "This is the message"
)

class TicketServiceTest {

    @Test
    fun `test create`() {
        val client = ClientMock.authenticatedClient
        ClientMock.mockCallSuccess(
            SlackDescriptions.sendSupport,
            Unit
        )
        val ticketService = TicketService(client)

        runBlocking {
            ticketService.createTicket(ticket)
        }
    }

    @Test(expected = RPCException::class)
    fun `test create - failure`() {
        val client = ClientMock.authenticatedClient
        ClientMock.mockCallError(
            SlackDescriptions.sendSupport,
            statusCode = HttpStatusCode.InternalServerError
        )
        val ticketService = TicketService(client)
        runBlocking {
            ticketService.createTicket(ticket)
        }
    }
}
