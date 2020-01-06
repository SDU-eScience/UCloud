package dk.sdu.cloud.pong.services

import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.pong.api.Message
import dk.sdu.cloud.pong.api.Streams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.random.Random

class StreamTest(private val streams: EventStreamService) {
    private val immediateTracker = ResultTracker("Immediate Event")
    private val batchedTracker = ResultTracker("Batched Event")

    fun startTest(): Job {
        return GlobalScope.launch {
            immediateTracker.start()
            batchedTracker.start()

            launch {
                sendMessages(Streams.immediate, immediateTracker)
            }

            launch {
                sendMessages(Streams.batched, batchedTracker)
            }
        }
    }

    private suspend fun CoroutineScope.sendMessages(stream: EventStream<Message>, tracker: ResultTracker) {
        val producer = streams.createProducer(stream)
        while (isActive) {
            val numberOfMessages = Random.nextInt(1, 10)
            coroutineScope {
                (0 until numberOfMessages).map {
                    launch {
                        tracker.trackResult(TestResult(System.currentTimeMillis(), true))
                        producer.produce(Message("Hello!"))
                    }
                }
            }.joinAll()

            delay(30_000)
        }
    }
}
