package dk.sdu.cloud.events

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class KafkaStreamService(
    private val consumerConfig: Properties,
    private val producerConfig: Properties,
    private val parallelism: Int
) : EventStreamService {
    private var started: Boolean = false
    private val threads = ArrayList<KafkaPollThread>()
    private val consumers = HashMap<EventStream<*>, EventConsumer<*>>()

    override suspend fun start() {
        started = true

        coroutineScope {
            repeat(parallelism) {
                consumers.forEach { (stream, rawConsumer) ->
                    @Suppress("UNCHECKED_CAST")
                    val consumer = rawConsumer as EventConsumer<Any>
                    val reader = defaultMapper.readerFor(stream.typeReference)
                    val thread = KafkaPollThread(consumerConfig, stream.name) { records ->
                        val events = records.mapNotNull {
                            runCatching {
                                reader.readValue<Any>(it)
                            }.getOrElse { ex ->
                                log.warn("Caught exception while parsing element from ${stream.name}")
                                log.warn("Exception was: ${ex.stackTraceToString()}")
                                log.warn("Value was: $it")
                                null
                            }
                        }

                        val shouldCommit = consumer.accept(events)
                        if (shouldCommit) {
                            commit()
                        }
                    }

                    threads.add(thread)

                    launch(Dispatchers.Default) {
                        thread.runLoop()
                    }
                }
            }
        }
    }

    override suspend fun stop() {
        threads.forEach { it.isRunning.set(false) }
    }

    override fun <V : Any> subscribe(stream: EventStream<V>, consumer: EventConsumer<V>) {
        if (started) {
            throw IllegalStateException("Cannot subscribe after start() has been called!")
        }

        consumers[stream] = consumer
    }

    override fun <V : Any> createProducer(stream: EventStream<V>): EventProducer<V> {
        return KafkaEventProducer(producerConfig, stream)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

internal class KafkaPollThread(
    private val config: Properties,
    private val topic: String,
    private val eventConsumer: suspend KafkaPollThread.(List<String>) -> Unit
) {
    var isRunning = AtomicBoolean(true)
    private var nextLivenessCheck = System.currentTimeMillis() + 10_000

    private var shouldCommit = false
    private val consumer: KafkaConsumer<String, String> by lazy {
        KafkaConsumer<String, String>(config).also {
            it.subscribe(listOf(topic))
        }
    }

    // Note: Should only be called from within the [eventConsumer]
    fun commit() {
        shouldCommit = true
    }

    private fun probeHeartbeat() {
        if (System.currentTimeMillis() > nextLivenessCheck) {
            if (secondsSinceLastHeartBeat.toInt() > 10) {
                throw IllegalStateException("KAFKA DEAD!")
            }

            nextLivenessCheck = System.currentTimeMillis() + 10_000
        }
    }

    private val secondsSinceLastHeartBeat: Number
        get() = consumer.metrics().entries
            .find { it.key.name() == "last-heartbeat-seconds-ago" }
            ?.value
            ?.metricValue() as Number

    private suspend fun poll() {
        probeHeartbeat()
        val records = consumer.poll(Duration.ofMillis(0))
        eventConsumer(records.map { it.value() })

        if (shouldCommit) {
            shouldCommit = false
            asyncCommit()
        }
    }

    private fun asyncCommit() {
        // The commitAsync API is useless. The callback is not called until we call poll again.
        consumer.commitAsync { _, exception ->
            if (exception != null) {
                throw exception
            }
        }
    }

    suspend fun runLoop() {
        consumer.use {
            log.debug("Starting consumption of Kafka topic: '$topic'")
            while (isRunning.get()) {
                poll()
                delay(100)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
