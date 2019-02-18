package dk.sdu.cloud.support.services

import dk.sdu.cloud.calls.RPCException
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SlackTest{

    @Test (expected = RPCException::class)
    fun `On Ticket test - failure of call`() {
        val slack = SlackNotifier("http://cloud.sdu.dk")
        runBlocking {
            slack.onTicket(ticket)
        }
    }
}
