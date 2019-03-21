package dk.sdu.cloud.kafka

import dk.sdu.cloud.service.ConsumedEvent
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventStreamProcessor
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Deprecated("Replace with new Kafka API")
data class KafkaConsumedEvent<V>(
    override val value: V,
    val partition: TopicPartition,
    val offset: Long,
    private val kafkaConsumer: KafkaConsumer<String, String>
) : ConsumedEvent<V> {
    override fun <V2> map(newValue: V2): ConsumedEvent<V2> = KafkaConsumedEvent(
        newValue,
        partition,
        offset,
        kafkaConsumer
    )

    override fun commit() {
        kafkaConsumer.commitSync(
            mapOf(partition to OffsetAndMetadata(offset + 1))
        )
    }
}

@Deprecated("Replace with new Kafka API")
class KafkaEventConsumer<K, V>(
    internalQueueSize: Int,
    private val pollTimeoutInMs: Long = 10,
    private val description: StreamDescription<K, V>,
    private val kafkaConsumer: KafkaConsumer<String, String>
) : EventConsumer<Pair<K, V>> {
    private val id = threadIdCounter.getAndIncrement()

    private var exceptionHandler: ((Throwable) -> Unit)? = null

    override var isRunning = false
        private set

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "kafka-${description.name}-poll-thread-$id") }
    private val queue = ArrayBlockingQueue<KafkaConsumedEvent<Pair<K, V>>>(internalQueueSize)
    private val overflowBuffer = ArrayList<KafkaConsumedEvent<Pair<K, V>>>()

    private val rootProcessor = object : EventStreamProcessor<Pair<K, V>, Pair<K, V>> {
        private val children = ArrayList<EventStreamProcessor<Pair<K, V>, *>>()

        override fun addChildProcessor(processor: EventStreamProcessor<Pair<K, V>, *>) {
            children.add(processor)
        }

        override fun accept(events: List<ConsumedEvent<Pair<K, V>>>) {
            children.forEach { it.accept(events) }
        }

        override fun commitConsumed(events: List<ConsumedEvent<*>>) {
            this@KafkaEventConsumer.commitConsumed(events)
        }
    }

    private val processingThread = Thread({
        var nextPrint = 0L
        while (isRunning) {
            try {
                // Block for first available element
                val next: KafkaConsumedEvent<Pair<K, V>>? = queue.poll(100, TimeUnit.MILLISECONDS)

                if (System.currentTimeMillis() > nextPrint) {
                    nextPrint = System.currentTimeMillis() + 10_000

                    log.debug(
                        "We retrieved an element from the processing thread!" +
                                "overflowBuffer.size = ${overflowBuffer.size} " +
                                "queue.size = ${queue.size}"
                    )
                }

                try {
                    if (next != null) {
                        val nextBatch = ArrayList<KafkaConsumedEvent<Pair<K, V>>>(queue.remainingCapacity() + 1)
                        nextBatch.add(next)
                        queue.drainTo(nextBatch) // Drain immediately if there are several elements in queue

                        rootProcessor.accept(nextBatch)
                    } else {
                        // If no element was available before deadline we just notify the root processor that we
                        // found nothing. This can then trigger emission of events that might be collecting in
                        // a buffer (for batch processing).
                        rootProcessor.accept(emptyList())
                    }
                } catch (ex: Throwable) {
                    log.info("Caught fatal exception in processing thread!")
                    log.info(ex.stackTraceToString())
                    exceptionHandler?.invoke(ex)

                    isRunning = false
                }
            } catch (ex: InterruptedException) {
                log.debug("Was interrupted")
                log.debug(ex.stackTraceToString())
            }
        }
    }, "kafka-${description.name}-processing-thread-$id")

    override fun configure(
        configure: (processor: EventStreamProcessor<*, Pair<K, V>>) -> Unit
    ): EventConsumer<Pair<K, V>> {
        if (isRunning) throw IllegalStateException("Already running")
        configure(rootProcessor)
        isRunning = true

        processingThread.start()
        executor.submit { internalPoll() }
        return this
    }

    private var nextPrint = 0L
    private fun internalPoll() {
        try {
            if (System.currentTimeMillis() > nextPrint) {
                nextPrint = System.currentTimeMillis() + 10_000
                log.debug(
                    "Current assignment is: " +
                            kafkaConsumer.assignment().joinToString {
                                "[topic=${it.topic()}, partition=${it.partition()}]"
                            }
                )
            }

            val events = kafkaConsumer
                .poll(Duration.ofMillis(pollTimeoutInMs))
                .map {
                    if (it.topic() != description.name) {
                        throw IllegalStateException(
                            "kafkaConsumer for some reason subscribed to different topic than " +
                                    "expected. Got '${it.topic()}' expected '${description.name}'"
                        )
                    }

                    KafkaConsumedEvent(
                        Pair(
                            description.keySerde.deserializer().deserialize(
                                description.name,
                                it.key().toByteArray()
                            ),
                            description.valueSerde.deserializer().deserialize(
                                description.name,
                                it.value().toByteArray()
                            )
                        ),

                        TopicPartition(it.topic(), it.partition()),
                        it.offset(),
                        kafkaConsumer
                    )
                }

            val remainingCapacity = queue.remainingCapacity()
            if (remainingCapacity < events.size) {
                // The queue will always have at least remainingCapacity since only the poll thread is adding.
                // We add as much as possible
                queue.addAll(events.slice(0 until remainingCapacity)) // expected to complete immediately

                // Rest goes into overflowBuffer (unbounded).
                overflowBuffer.addAll(events.slice(remainingCapacity until events.size))

                // Ask kafka not to deliver any more items (for any partition assigned to this thread).
                // We will resume once the overflowBuffer has been emptied into the queue
                kafkaConsumer.pause(kafkaConsumer.assignment())

                log.info("Could not process elements in topics: ${kafkaConsumer.assignment()}")
                log.info("Pausing for now. ${overflowBuffer.size} elements have been added to overflow buffer")
            } else {
                if (overflowBuffer.isNotEmpty()) {
                    // Add all new events to overflowBuffer.
                    // When we are recovering we don't expect this to happen a lot.
                    if (log.isDebugEnabled && events.isNotEmpty()) {
                        log.debug("Adding ${events.size} additional events to overflow")
                    }

                    overflowBuffer.addAll(events)

                    // Offer as much as possible to the queue and break
                    var offeredSuccessfully = 0
                    val it = overflowBuffer.iterator()
                    while (it.hasNext()) {
                        val next = it.next()
                        val success = queue.offer(next)

                        if (success) {
                            it.remove()
                            offeredSuccessfully++
                        } else {
                            break
                        }
                    }

                    if (System.currentTimeMillis() > nextPrint) {
                        nextPrint = System.currentTimeMillis() + 10_000
                        log.debug("We managed to move $offeredSuccessfully from the overflow into the queue.")
                        log.debug("We currently have ${overflowBuffer.size} elements in the overflow buffer.")
                    }
                } else {
                    queue.addAll(events) // expected to complete immediately
                }

                val paused = kafkaConsumer.paused()
                if (paused.isNotEmpty() && overflowBuffer.isEmpty()) {
                    kafkaConsumer.resume(paused)
                    log.info("Overflow buffer is now empty.")
                    log.info("Resuming for the following assignment: ${kafkaConsumer.assignment()}")
                }
            }

            executor.submit {
                if (isRunning) {
                    internalPoll()

                    if (!processingThread.isAlive) {
                        log.warn("For some reason the processing thread is no longer alive!")
                    }
                }
            }
        } catch (ex: Throwable) {
            log.warn("Caught internal exception in KafkaConsumer")
            log.warn(ex.stackTraceToString())
            exceptionHandler?.invoke(ex)
            close()
        }
    }

    override fun close() {
        executor.submit {
            isRunning = false
            kafkaConsumer.close()
            executor.shutdown()
            processingThread.interrupt()
        }
    }

    override fun commitConsumed(events: List<ConsumedEvent<*>>) {
        if (events.isEmpty()) return
        events.first() as? KafkaConsumedEvent<*> ?: throw IllegalArgumentException(
            "Expected events to be of type " +
                    "${KafkaConsumedEvent::class.java.name}. But instead got ${events.first().javaClass.name}"
        )

        @Suppress("UNCHECKED_CAST")
        val recastEvents = events as List<KafkaConsumedEvent<*>>

        executor.submit {
            kafkaConsumer.commitSync(
                recastEvents
                    .asSequence()
                    .groupBy { it.partition }
                    .map { (topicAndPartition, eventsForPartition) ->
                        Pair(
                            topicAndPartition,
                            OffsetAndMetadata(eventsForPartition.maxBy { it.offset }!!.offset + 1)
                        )
                    }
                    .toList()
                    .toMap()
            )
        }
    }

    override fun onExceptionCaught(handler: (Throwable) -> Unit) {
        exceptionHandler = handler
    }

    companion object : Loggable {
        override val log = logger()

        private val threadIdCounter = AtomicInteger(0)
    }
}
