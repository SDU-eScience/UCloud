package dk.sdu.cloud.ping.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.server.IngoingCall
import dk.sdu.cloud.pong.api.PongServiceDescription
import dk.sdu.cloud.pong.api.Pongs
import dk.sdu.cloud.pong.api.RegularCallRequest
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.random.Random

class RegularCallTest(testName: String, private val client: AuthenticatedClient) {
    private val tracker = ResultTracker(testName)

    fun startTest(): Job {
        tracker.start()

        return GlobalScope.launch {
            var count = 0L
            while (isActive) {
                val numberOfConcurrentRequests = Random.nextInt(1, 10)

                (0 until numberOfConcurrentRequests).map { _ ->
                    launch {
                        val startTime = System.currentTimeMillis()
                        val resp = Pongs.regularCall.call(RegularCallRequest("Message $count"), client)
                        val endTime = System.currentTimeMillis()

                        tracker.trackResult(TestResult(startTime, resp is IngoingCallResponse.Ok, endTime - startTime))
                    }
                }.joinAll()

                count++
                delay(30_000)
            }
        }
    }
}
