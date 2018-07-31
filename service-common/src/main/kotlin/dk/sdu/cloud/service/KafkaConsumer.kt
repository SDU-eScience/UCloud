package dk.sdu.cloud.service

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.io.Closeable
import java.util.concurrent.Executors

interface ConsumedEvent<V> {
    val value: V

    fun <V2> map(newValue: V2): ConsumedEvent<V2>

    /**
     * Commits to the [EventConsumer] that this message, _and all before it_, have been consumed successfully.
     *
     * Note: The [EventConsumer] might have additional partitions, in order to improve parallelism, because of this
     * this method should be invoked on all events if [EventConsumer.commitConsumed] is not used. It is not possible to
     * skip some and then call [commit] on a single [ConsumedEvent].
     */
    fun commit()
}

interface EventConsumer<V> : Closeable {
    val isRunning: Boolean

    fun configure(configure: (processor: EventStreamProcessor<*, V>) -> Unit): EventConsumer<V>
    fun commitConsumed()
    fun onExceptionCaught(handler: (Throwable) -> Unit)
}

interface EventConsumerFactory {
    // TODO Kafka _insists_ on enforcing thread safety (even when what we do is safe).
    // Because of this we have to take complete ownership of the thread. As a result, we can easily end up with an
    // explosion of threads, i.e. (parallelism * threads). Kafka's way of dealing with this is to subscribe to multiple
    // topics and then demultiplex them. For performance reasons we should do that also.
    fun <K, V> createConsumer(description: StreamDescription<K, V>): EventConsumer<Pair<K, V>>
}

interface EventStreamProcessor<ValueIn, ValueOut> {
    fun addChildProcessor(processor: EventStreamProcessor<ValueOut, *>)
    fun accept(events: List<ConsumedEvent<ValueIn>>)
    fun commitConsumed()
}

abstract class AbstractEventStreamProcessor<ValueIn, ValueOut>(
    private val parent: EventStreamProcessor<*, ValueIn>
) : EventStreamProcessor<ValueIn, ValueOut> {
    private val children = ArrayList<EventStreamProcessor<ValueOut, *>>()

    internal abstract fun handleEvents(events: List<ConsumedEvent<ValueIn>>): List<ConsumedEvent<ValueOut>>

    override fun addChildProcessor(processor: EventStreamProcessor<ValueOut, *>) {
        children.add(processor)
    }

    override fun accept(events: List<ConsumedEvent<ValueIn>>) {
        val output = handleEvents(events)
        children.forEach { it.accept(output) }
    }

    override fun commitConsumed() {
        parent.commitConsumed()
    }
}

fun <ValueIn, ValueOut> EventStreamProcessor<*, ValueIn>.addProcessor(
    handler: EventStreamProcessor<ValueIn, ValueOut>.(events: List<ConsumedEvent<ValueIn>>) -> List<ConsumedEvent<ValueOut>>
): EventStreamProcessor<ValueIn, ValueOut> {
    return object : AbstractEventStreamProcessor<ValueIn, ValueOut>(this) {
        override fun handleEvents(events: List<ConsumedEvent<ValueIn>>): List<ConsumedEvent<ValueOut>> =
            handler(events)
    }.also { addChildProcessor(it) }
}

fun <ValueIn, ValueOut> EventStreamProcessor<*, ValueIn>.addValueProcessor(
    handler: EventStreamProcessor<ValueIn, ValueOut>.(events: ValueIn) -> ValueOut
): EventStreamProcessor<ValueIn, ValueOut> {
    return object : AbstractEventStreamProcessor<ValueIn, ValueOut>(this) {
        override fun handleEvents(events: List<ConsumedEvent<ValueIn>>): List<ConsumedEvent<ValueOut>> =
            events.map { it.map(handler(it.value)) }
    }.also { addChildProcessor(it) }
}

class BatchedEventStreamProcessor<V>(
    private val parent: EventStreamProcessor<*, V>,
    private val batchTimeout: Long,
    private val maxBatchSize: Int
) : EventStreamProcessor<V, V> {
    private val batch = ArrayList<ConsumedEvent<V>>()
    private var nextTimedEmit = System.currentTimeMillis() + batchTimeout
    private val children = ArrayList<EventStreamProcessor<V, *>>()

    init {
        parent.addChildProcessor(this)
    }

    override fun addChildProcessor(processor: EventStreamProcessor<V, *>) {
        children.add(processor)
    }

    override fun accept(events: List<ConsumedEvent<V>>) {
        for (item in events) {
            batch.add(item)

            if (batch.size >= maxBatchSize) emit()
        }

        if (batch.isNotEmpty() && System.currentTimeMillis() >= nextTimedEmit) emit()
    }

    private fun emit() {
        assert(batch.size <= maxBatchSize)
        val copyOfEvents = batch.toList()
        children.forEach { it.accept(copyOfEvents) }
        batch.clear()
        nextTimedEmit = System.currentTimeMillis() + batchTimeout
    }

    override fun commitConsumed() {
        parent.commitConsumed()
    }
}

fun <V> EventStreamProcessor<*, V>.batched(batchTimeout: Long, maxBatchSize: Int): EventStreamProcessor<V, V> {
    return BatchedEventStreamProcessor(this, batchTimeout, maxBatchSize)
}

fun <V> EventStreamProcessor<*, V>.consumeBatchAndCommit(handler: (List<V>) -> Unit) {
    addProcessor<V, Unit> {
        if (it.isNotEmpty()) {
            handler(it.map { it.value })
            commitConsumed()
        }

        emptyList()
    }
}

fun <V> EventStreamProcessor<*, V>.consumeAndCommit(handler: (V) -> Unit) {
    addProcessor<V, Unit> {
        it.forEach { handler(it.value) }
        commitConsumed()
        emptyList()
    }
}

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

class KafkaEventConsumer<K, V>(
    private val pollTimeoutInMs: Long = 10,
    private val description: StreamDescription<K, V>,
    private val kafkaConsumer: KafkaConsumer<String, String>
) : EventConsumer<Pair<K, V>> {
    private var exceptionHandler: ((Throwable) -> Unit)? = null

    override var isRunning = false
        private set

    private val executor = Executors.newSingleThreadExecutor()

    private val rootProcessor = object : EventStreamProcessor<Pair<K, V>, Pair<K, V>> {
        private val children = ArrayList<EventStreamProcessor<Pair<K, V>, *>>()

        override fun addChildProcessor(processor: EventStreamProcessor<Pair<K, V>, *>) {
            children.add(processor)
        }

        override fun accept(events: List<ConsumedEvent<Pair<K, V>>>) {
            children.forEach { it.accept(events) }
        }

        override fun commitConsumed() {
            this@KafkaEventConsumer.commitConsumed()
        }
    }

    override fun configure(
        configure: (processor: EventStreamProcessor<*, Pair<K, V>>) -> Unit
    ): EventConsumer<Pair<K, V>> {
        if (isRunning) throw IllegalStateException("Already running")
        val root = rootProcessor
        configure(root)
        isRunning = true

        executor.submit { internalPoll(root) }
        return this
    }

    private fun internalPoll(root: EventStreamProcessor<Pair<K, V>, *>) {
        try {
            val events = kafkaConsumer
                .poll(pollTimeoutInMs)
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

            try {
                root.accept(events)
            } catch (ex: Exception) {
                log.warn("Caught exception in KafkaConsumer while handing off to the EventStreamProcessor!")
                log.warn(ex.stackTraceToString())
                exceptionHandler?.invoke(ex)
                close()
            }

            executor.submit {
                if (isRunning) internalPoll(root)
            }
        } catch (ex: Exception) {
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
        }
    }

    override fun commitConsumed() {
        executor.submit { kafkaConsumer.commitSync() }
    }

    override fun onExceptionCaught(handler: (Throwable) -> Unit) {
        exceptionHandler = handler
    }

    companion object : Loggable {
        override val log = logger()
    }
}
