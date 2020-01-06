package dk.sdu.cloud.ping.services

import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.pong.api.Streams

class ConsumerTest(private val streams: EventStreamService) {
    private val immediateTracker = ResultTracker("Immediate Consumer")
    private val batchedTracker = ResultTracker("Batched Consumer")

    fun startTest() {
        immediateTracker.start()
        batchedTracker.start()

        streams.subscribe(Streams.immediate, EventConsumer.Immediate { _ ->
            immediateTracker.trackResult(TestResult(System.currentTimeMillis(), true))
        })

        streams.subscribe(Streams.batched, EventConsumer.Batched { messages ->
            messages.forEach { _ ->
                batchedTracker.trackResult(TestResult(System.currentTimeMillis(), true))
            }
        })
    }
}
