package dk.sdu.cloud.service

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.io.Closeable

interface ConsumedEvent<K, V> {
    val key: K
    val value: V

    /**
     * Commits to the [EventConsumer] that this message, _and all before it_, have been consumed successfully.
     *
     * Note: The [EventConsumer] might have additional partitions, in order to improve parallelism, because of this
     * this method should be invoked on all events if [EventConsumer.commitAll] is not used. It is not possible to
     * skip some and then call [commit] on a single [ConsumedEvent].
     */
    fun commit()
}

interface EventConsumer<K, V> : Closeable {
    val isRunning: Boolean

    fun poll(timeout: Long = 10): List<ConsumedEvent<K, V>>
    fun commitAll()
}

interface EventConsumerFactory {
    // TODO Kafka _insists_ on enforcing thread safety (even when what we do is safe).
    // Because of this we have to take complete ownership of the thread. As a result, we can easily end up with an
    // explosion of threads, i.e. parallelism * threads. Kafka's way of dealing with this is to subscribe to multiple
    // topics and then demultiplex them. For performance reasons we should do that also.
    fun <K, V> createConsumer(description: StreamDescription<K, V>): EventConsumer<K, V>
}

fun <K, V> EventConsumer<K, V>.forEach(timeoutPerPollInMs: Long = 10, handler: (K, V) -> Unit) {
    while (isRunning) {
        poll(timeoutPerPollInMs).forEach { handler(it.key, it.value) }
        commitAll()
    }
}

fun <K, V> EventConsumer<K, V>.batched(
    batchTimeout: Long,
    maxBatchSize: Int,
    timeoutPerPollInMs: Long = 10,
    handler: (batch: List<Pair<K, V>>) -> Unit
) {
    val batch = ArrayList<Pair<K, V>>()
    var nextTimedEmit = System.currentTimeMillis() + batchTimeout

    fun emit() {
        assert(batch.isNotEmpty())
        assert(batch.size <= maxBatchSize)

        handler(batch.toList())
        batch.clear()
        commitAll()
        nextTimedEmit = System.currentTimeMillis() + batchTimeout
    }

    while (isRunning) {
        val items = poll(timeoutPerPollInMs)
        for (item in items) {
            batch.add(item.key to item.value)

            if (batch.size > maxBatchSize) emit()
        }

        if (batch.isNotEmpty() && (System.currentTimeMillis() >= nextTimedEmit || !isRunning)) emit()
    }
}

data class KafkaConsumedEvent<K, V>(
    override val key: K,
    override val value: V,
    val partition: TopicPartition,
    val offset: Long,
    private val kafkaConsumer: KafkaConsumer<String, String>
) : ConsumedEvent<K, V> {
    override fun commit() {
        kafkaConsumer.commitSync(
            mapOf(
                partition to OffsetAndMetadata(offset + 1)
            )
        )
    }
}

class KafkaEventConsumer<K, V>(
    private val description: StreamDescription<K, V>,
    private val kafkaConsumer: KafkaConsumer<String, String>
) : EventConsumer<K, V> {
    override var isRunning = true
        private set

    override fun poll(timeout: Long): List<ConsumedEvent<K, V>> {
        return kafkaConsumer.poll(timeout).map {
            KafkaConsumedEvent(
                description.keySerde.deserializer().deserialize(description.name, it.key().toByteArray()),
                description.valueSerde.deserializer().deserialize(description.name, it.value().toByteArray()),
                TopicPartition(it.topic(), it.partition()),
                it.offset(),
                kafkaConsumer
            )
        }
    }

    override fun close() {
        isRunning = false
        kafkaConsumer.close()
    }

    override fun commitAll() {
        kafkaConsumer.commitSync()
    }
}
