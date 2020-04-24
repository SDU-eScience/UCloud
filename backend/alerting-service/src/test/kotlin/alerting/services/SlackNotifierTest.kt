package dk.sdu.cloud.alerting.services

import dk.sdu.cloud.calls.RPCException
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SlackNotifierTest{

    @Test (expected = RPCException::class)
    fun `On Alert test - failure`() {
        val slack = SlackNotifier("http://cloud.sdu.dk")
        runBlocking {
            slack.onAlert(alert)
        }
    }
}
