package dk.sdu.cloud.service

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.*
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import kotlin.reflect.KClass

fun <K, V> StreamsBuilder.stream(description: StreamDescription<K, V>): KStream<K, V> =
    stream(description.name, Consumed.with(description.keySerde, description.valueSerde))

fun <K, V> StreamsBuilder.table(description: StreamDescription<K, V>): KTable<K, V> =
    table(description.name, Consumed.with(description.keySerde, description.valueSerde))

fun <K, V, A> StreamsBuilder.aggregate(
    description: StreamDescription<K, V>,
    tableDescription: TableDescription<K, A>,
    initializer: () -> A? = { null },
    aggregate: (K, V, A?) -> A
): KTable<K, A> {
    val materializedAs = Materialized.`as`<K, A, KeyValueStore<Bytes, ByteArray>>(tableDescription.name)
        .withKeySerde(tableDescription.keySerde)
        .withValueSerde(tableDescription.valueSerde)

    return stream(description).groupByKey(Serialized.with(description.keySerde, description.valueSerde)).aggregate(
        initializer,
        aggregate,
        materializedAs
    )
}

fun <K, V, A> KGroupedStream<K, V>.aggregate(
    target: TableDescription<K, A>,
    initializer: () -> A? = { null },
    aggregate: (K, V, A?) -> A
) {
    val materializedAs = Materialized.`as`<K, A, KeyValueStore<Bytes, ByteArray>>(target.name)
        .withKeySerde(target.keySerde)
        .withValueSerde(target.valueSerde)
    aggregate(initializer, aggregate, materializedAs)
}

fun <K, V : Any, R : V> KStream<K, V>.filterIsInstance(klass: KClass<R>) =
    filter { _, value -> klass.isInstance(value) }.mapValues {
        @Suppress("UNCHECKED_CAST")
        it as R
    }

fun <K, V> KStream<K, V>.toTable(): KTable<K, V> = groupByKey().reduce { _, newValue -> newValue }
fun <K, V> KStream<K, V>.toTable(keySerde: Serde<K>, valSerde: Serde<V>): KTable<K, V> =
    groupByKey(Serialized.with(keySerde, valSerde)).reduce { _, newValue -> newValue }

fun <K, V> KStream<K, V>.through(description: StreamDescription<K, V>): KStream<K, V> =
    through(description.name, Produced.with(description.keySerde, description.valueSerde))

fun <K, V> KStream<K, V>.to(description: StreamDescription<K, V>) {
    to(description.name, Produced.with(description.keySerde, description.valueSerde))
}

class KafkaServices(
    private val streamsConfig: Properties,
    private val consumerConfig: Properties,
    val producer: KafkaProducer<String, String>,
    val adminClient: AdminClient?
) : EventConsumerFactory {
    fun build(block: Topology): KafkaStreams {
        return KafkaStreams(block, streamsConfig)
    }

    // TODO Event demultiplexing for performance. This will create a thread per topic processor
    override fun <K, V> createConsumer(
        description: StreamDescription<K, V>,
        internalQueueSize: Int
    ): EventConsumer<Pair<K, V>> {
        val consumer = KafkaConsumer<String, String>(consumerConfig)
        consumer.subscribe(listOf(description.name))
        return KafkaEventConsumer(internalQueueSize, 10, description, consumer)
    }
}

@Suppress("RedundantVisibilityModifier", "MemberVisibilityCanBePrivate")
object KafkaUtil {
    public fun retrieveKafkaStreamsConfiguration(config: ConnectionConfig): Properties {
        return retrieveKafkaStreamsConfiguration(config.kafka, config.service)
    }

    public fun retrieveKafkaStreamsConfiguration(
        kafkaConnectionConfig: KafkaConnectionConfig,
        serviceConfig: ServiceConnectionConfig
    ): Properties = Properties().apply {
        this[StreamsConfig.APPLICATION_ID_CONFIG] = serviceConfig.description.name
        this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConnectionConfig.servers.joinToString(",") { it.toString() }
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest" // TODO This should probably be changed back
        this[StreamsConfig.APPLICATION_SERVER_CONFIG] = "${serviceConfig.hostname}:${serviceConfig.port}"

        // The defaults do not use java.io.tmpdir
        this[StreamsConfig.STATE_DIR_CONFIG] = File(System.getProperty("java.io.tmpdir"), "kafka-streams").absolutePath
    }

    public fun retrieveKafkaProducerConfiguration(config: ConnectionConfig): Properties {
        return retrieveKafkaProducerConfiguration(config.kafka)
    }

    public fun retrieveKafkaProducerConfiguration(kafkaServers: KafkaConnectionConfig): Properties =
        Properties().apply {
            this[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaServers.servers.joinToString(",") { it.toString() }
            this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
            this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
        }

    public fun retrieveConsumerConfig(config: ConnectionConfig): Properties = Properties().apply {
        this[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = config.kafka.servers.joinToString(",") { it.toString() }
        this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.qualifiedName!!
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.qualifiedName!!
        this[ConsumerConfig.GROUP_ID_CONFIG] = config.service.description.name + "-consumer"
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    }

    /**
     * Creates kafka streams based on the defaults as defined in [KafkaUtil]
     *
     * It is possible to change the configuration used by passing either [streamsConfigBody] or [producerConfigBody]
     */
    public inline fun createKafkaServices(
        configuration: ServerConfiguration,
        streamsConfigBody: (Properties) -> Unit = {},
        consumerConfigBody: (Properties) -> Unit = {},
        producerConfigBody: (Properties) -> Unit = {},
        createAdminClient: Boolean = false,
        log: Logger = LoggerFactory.getLogger(KafkaUtil::class.java)
    ): KafkaServices {
        log.info("Connecting to Kafka")
        val streamsConfig = retrieveKafkaStreamsConfiguration(configuration.connConfig).also(streamsConfigBody)
        val producerConfig = retrieveKafkaProducerConfiguration(configuration.connConfig).also(producerConfigBody)
        val consumerConfig = retrieveConsumerConfig(configuration.connConfig).also(consumerConfigBody)
        val producer = KafkaProducer<String, String>(producerConfig)
        val adminClient: AdminClient? = if (createAdminClient) {
            AdminClient.create(mapOf(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to
                        configuration.connConfig.kafka.servers.joinToString(",") { it.toString() }
            ))
        } else null

        log.info("Connected to Kafka")
        return KafkaServices(streamsConfig, consumerConfig, producer, adminClient)
    }
}
