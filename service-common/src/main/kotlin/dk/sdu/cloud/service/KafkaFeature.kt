package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import java.io.File
import java.util.*

data class KafkaHostConfig(
    val hostname: String,
    val port: Int = 9092
) {
    override fun toString(): String = "$hostname:$port"
}

class KafkaServices(
    private val streamsConfig: Properties,
    private val consumerConfig: Properties,
    val producer: KafkaProducer<String, String>,
    val adminClient: AdminClient
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

class KafkaFeatureConfiguration(
    internal val streamsConfigBody: (Properties) -> Unit = {},
    internal val consumerConfigBody: (Properties) -> Unit = {},
    internal val producerConfigBody: (Properties) -> Unit = {}
)

class KafkaFeature(
    private val config: KafkaFeatureConfiguration
) : MicroFeature {

    private fun retrieveKafkaStreamsConfiguration(
        servers: List<KafkaHostConfig>,
        serviceDescription: ServiceDescription
    ): Properties = Properties().apply {
        this[StreamsConfig.APPLICATION_ID_CONFIG] = serviceDescription.name
        this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = servers.joinToString(",") { it.toString() }
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "latest" // TODO This should probably be changed back

        // The defaults do not use java.io.tmpdir
        this[StreamsConfig.STATE_DIR_CONFIG] = File(System.getProperty("java.io.tmpdir"), "kafka-streams").absolutePath
    }

    private fun retrieveKafkaProducerConfiguration(servers: List<KafkaHostConfig>): Properties =
        Properties().apply {
            this[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = servers.joinToString(",") { it.toString() }
            this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
            this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
        }

    private fun retrieveConsumerConfig(
        servers: List<KafkaHostConfig>,
        serviceDescription: ServiceDescription
    ): Properties = Properties().apply {
        this[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = servers.joinToString(",") { it.toString() }
        this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.qualifiedName!!
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.qualifiedName!!
        this[ConsumerConfig.GROUP_ID_CONFIG] = serviceDescription.name + "-consumer"
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
    }

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(ConfigurationFeature)

        log.info("Connecting to Kafka")

        val hosts = ctx.configuration.requestChunkAtOrNull("kafka", "brokers") ?: run {
            log.info("No available configuration found at 'kafka/brokers'.")
            log.info("Attempting to look for defaults.")

            val hostname = findValidHostname(DEFAULT_HOST_NAMES)
                    ?: throw IllegalStateException("Could not find a valid kafka host")

            log.info("$hostname is a valid host, assuming Kafka is running on this machine.")

            listOf(KafkaHostConfig(hostname))
        }

        val streamsConfig = retrieveKafkaStreamsConfiguration(hosts, ctx.serviceDescription)
            .also(config.streamsConfigBody)
        val producerConfig = retrieveKafkaProducerConfiguration(hosts).also(config.producerConfigBody)
        val consumerConfig = retrieveConsumerConfig(hosts, ctx.serviceDescription).also(config.consumerConfigBody)

        val producer = KafkaProducer<String, String>(producerConfig)
        val adminClient: AdminClient = AdminClient.create(mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to hosts.joinToString(",") { it.toString() }
        ))

        log.info("Connected to Kafka")

        ctx.kafka = KafkaServices(streamsConfig, consumerConfig, producer, adminClient)
    }

    companion object Feature : MicroFeatureFactory<KafkaFeature, KafkaFeatureConfiguration>, Loggable {
        override val key = MicroAttributeKey<KafkaFeature>("kafka-feature")
        override fun create(config: KafkaFeatureConfiguration): KafkaFeature = KafkaFeature(config)

        override val log = logger()

        internal val SERVICES_KEY = MicroAttributeKey<KafkaServices>("kafka-services")

        private val DEFAULT_HOST_NAMES = listOf(
            "kafka",
            "localhost"
        )
    }
}

var Micro.kafka: KafkaServices
    get() {
        requireFeature(KafkaFeature)
        return attributes[KafkaFeature.SERVICES_KEY]
    }
    internal set(value) {
        attributes[KafkaFeature.SERVICES_KEY] = value
    }