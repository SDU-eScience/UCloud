package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal val alert = Alert(
    "This is the alert message"
)

class AlertingServiceTest{

    private val slack = mockk<SlackNotifier>()

    @Test
    fun `test create`() {
        coEvery { slack.onAlert(any()) } just runs
        val alertService = AlertingService(listOf(slack))

        runBlocking {
            alertService.createAlert(alert)
        }
    }

    @Test(expected = RPCException::class)
    fun `test create - failure`() {
        coEvery { slack.onAlert(any()) } throws RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        val ticketService = AlertingService(listOf(slack))

        runBlocking {
            ticketService.createAlert(alert)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test create - failure - empty notifier list`() {
        AlertingService(emptyList())
    }
}
