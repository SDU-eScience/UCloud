package dk.sdu.cloud.events

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewPartitions
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class KafkaStreamService(
    private val consumerConfig: Properties,
    private val producerConfig: Properties,
    private val parallelism: Int,
    private val devMode: Boolean
) : EventStreamService {

    private var started: Boolean = false
    private val threads = ArrayList<KafkaPollThread>()
    private val consumers = HashMap<EventStream<*>, EventConsumer<*>>()

    override fun start() {
        started = true

        if (consumers.isEmpty()) return

        (0 until parallelism).forEach {
            consumers.forEach { (stream, rawConsumer) ->
                @Suppress("UNCHECKED_CAST")
                val consumer = rawConsumer as EventConsumer<Any>
                val thread = KafkaPollThread(consumerConfig, stream.name) { records ->
                    val events = records.mapNotNull {
                        runCatching {
                            stream.deserialize(it)
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

                thread.start()
            }

        }
    }

    override fun stop() {
        threads.forEach { it.isRunning.set(false) }
    }

    override fun <V : Any> subscribe(
        stream: EventStream<V>,
        consumer: EventConsumer<V>,
        rescheduleIdleJobsAfterMs: Long
    ) {
        if (started) {
            throw IllegalStateException("Cannot subscribe after start() has been called!")
        }

        consumers[stream] = consumer
    }

    override fun <V : Any> createProducer(stream: EventStream<V>): EventProducer<V> {
        return KafkaEventProducer(producerConfig, stream)
    }

    override fun createStreams(streams: List<EventStream<*>>) {
        createAdminClient().use { adminClient ->
            val streamsByName = streams.associateBy { it.name }
            val allTopics = describeStreams(streams.map { it.name })

            run {
                // Create new topics
                val missingTopics = allTopics.filterValues { it == null }.keys
                val topicsToCreate = missingTopics.map { topic ->
                    val stream = streamsByName.getValue(topic)
                    NewTopic(
                        stream.name,
                        stream.desiredPartitions ?: DEFAULT_PARTITIONS,
                        stream.desiredReplicas ?: if (devMode) 1 else DEFAULT_REPLICAS
                    )
                }

                adminClient.createTopics(topicsToCreate).all().get()
            }

            run {
                // Modify existing topics
                val partitionsToCreate = allTopics.values.asSequence().filterNotNull().mapNotNull {
                    val stream = streamsByName[it.name] ?: return@mapNotNull null
                    val numPartitions = it.partitions
                    val desiredPartitions = stream.desiredPartitions ?: DEFAULT_PARTITIONS

                    when {
                        numPartitions == null -> null

                        desiredPartitions > numPartitions -> {
                            log.info(
                                "Increasing number of partitions from $desiredPartitions to $numPartitions for topic " +
                                        "'${stream.name}'"
                            )

                            stream.name to NewPartitions.increaseTo(desiredPartitions)
                        }

                        desiredPartitions < numPartitions -> {
                            log.info(
                                "The desired number of partitions for topic '${stream.name}' is lower than actual " +
                                        "number of partitions ($numPartitions > $desiredPartitions). " +
                                        "It is not possible to migrate this automatically. Manual migration is needed."
                            )
                            null
                        }

                        else -> null
                    }
                }.toMap()

                if (partitionsToCreate.isNotEmpty()) {
                    adminClient.createPartitions(partitionsToCreate).all().get()
                }
            }
        }
    }

    private fun createAdminClient(): AdminClient = AdminClient.create(
        mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to producerConfig[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG]
        )
    )

    override fun describeStreams(names: List<String>): Map<String, EventStreamState?> {
        createAdminClient().use { adminClient ->
            return describeStreams(adminClient, names)
        }
    }

    private fun describeStreams(
        adminClient: AdminClient,
        names: List<String>
    ): Map<String, EventStreamState?> {
        val describeFuture = adminClient.describeTopics(names)
        return describeFuture.values().map { (topicName, future) ->
            try {
                val topicDescription = future.get()
                log.debug("Got back the following result: $topicDescription")
                log.debug(topicDescription.name())
                topicName to EventStreamState(
                    topicDescription.name(),
                    topicDescription.partitions().size,
                    null
                )
            } catch (ex: Exception) {
                if (ex.cause is UnknownTopicOrPartitionException) {
                    topicName to null
                } else {
                    log.debug("Caught exception for $topicName")
                    log.debug(ex.stackTraceToString())
                    throw ex
                }
            }
        }.toMap()
    }

    companion object : Loggable {
        override val log = logger()

        private const val DEFAULT_PARTITIONS = 32
        private const val DEFAULT_REPLICAS: Short = 3
    }
}

internal class KafkaPollThread(
    private val config: Properties,
    private val topic: String,
    private val eventConsumer: suspend KafkaPollThread.(List<String>) -> Unit
) : Thread() {
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

    override fun run() {
        consumer.use {
            log.debug("Starting consumption of Kafka topic: '$topic'")
            runBlocking {
                while (isRunning.get()) {
                    poll()
                    delay(100)
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
