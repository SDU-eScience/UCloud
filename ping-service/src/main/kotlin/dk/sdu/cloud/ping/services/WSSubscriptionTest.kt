package dk.sdu.cloud.ping.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.subscribe
import dk.sdu.cloud.pong.api.Pongs
import dk.sdu.cloud.pong.api.SubscriptionRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WSSubscriptionTest(private val wsClient: AuthenticatedClient) {
    private val tracker = ResultTracker("WS Subscription Test")

    fun startTest(): Job {
        tracker.start()

        return GlobalScope.launch {
            while (isActive) {
                try {
                    val resp = Pongs.subscription.subscribe(
                        SubscriptionRequest("Message"),
                        wsClient,
                        handler = {
                            tracker.trackResult(TestResult(System.currentTimeMillis(), true))
                        }
                    )

                    tracker.trackResult(TestResult(System.currentTimeMillis(), resp is IngoingCallResponse.Ok))
                } catch (ex: Throwable) {
                    log.warn(ex.stackTraceToString())
                    tracker.trackResult(TestResult(System.currentTimeMillis(), false))
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
