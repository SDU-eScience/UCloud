package dk.sdu.cloud.service

import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.util.concurrent.Executors

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