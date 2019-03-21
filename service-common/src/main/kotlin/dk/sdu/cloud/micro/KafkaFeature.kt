package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.events.KafkaStreamService
import dk.sdu.cloud.kafka.KafkaEventConsumer
import dk.sdu.cloud.kafka.StreamDescription
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.findValidHostname
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

data class KafkaHostConfig(
    val hostname: String,
    val port: Int = 9092
) {
    override fun toString(): String = "$hostname:$port"
}

private const val POLL_TIMEOUT_IN_MS = 10L

@Deprecated("Replace with new Kafka API")
interface KafkaServices {
    val producer: Producer<String, String>
    val adminClient: AdminClient
    val defaultPartitions: Int
    val defaultReplicas: Short
}

@Deprecated("Replace with new Kafka API")
class KafkaServicesImpl(
    val consumerConfig: Properties,
    val producerConfig: Properties,
    override val defaultPartitions: Int = 32,
    override val defaultReplicas: Short = 1
) : EventConsumerFactory, KafkaServices {
    override val producer by lazy {
        KafkaProducer<String, String>(producerConfig)
    }

    override val adminClient by lazy {
        AdminClient.create(
            mapOf(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to producerConfig[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG]
            )
        )
    }

    // TODO Event demultiplexing for performance. This will create a thread per topic processor
    override fun <K, V> createConsumer(
        description: StreamDescription<K, V>,
        internalQueueSize: Int
    ): EventConsumer<Pair<K, V>> {
        val consumer = KafkaConsumer<String, String>(consumerConfig)
        consumer.subscribe(listOf(description.name))
        return KafkaEventConsumer(
            internalQueueSize,
            POLL_TIMEOUT_IN_MS,
            description,
            consumer
        )
    }
}

class KafkaFeatureConfiguration(
    internal val consumerConfigBody: (Properties) -> Unit = {},
    internal val producerConfigBody: (Properties) -> Unit = {},
    internal val kafkaServicesOverride: KafkaServices? = null
)

class KafkaFeature(
    private val config: KafkaFeatureConfiguration
) : MicroFeature {
    private fun retrieveKafkaProducerConfiguration(servers: List<KafkaHostConfig>): Properties =
        Properties().apply {
            this[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = servers.joinToString(",") { it.toString() }
            this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
            this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
            this[ProducerConfig.ACKS_CONFIG] = "all"
            this[ProducerConfig.MAX_BLOCK_MS_CONFIG] = "15000"
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
        this[ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG] = "60000"
    }

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        if (config.kafkaServicesOverride != null) {
            log.info("overriding kafka services")
            ctx.kafka = config.kafkaServicesOverride
        } else {
            ctx.requireFeature(ConfigurationFeature)

            log.info("Connecting to Kafka")

            val userConfig = ctx.configuration.requestChunkAtOrNull("kafka") ?: KafkaUserConfig()
            val hosts = userConfig.brokers?.takeIf { it.isNotEmpty() } ?: run {
                log.info("No available configuration found at 'kafka/brokers'.")
                log.info("Attempting to look for defaults.")

                val hostname = findValidHostname(DEFAULT_HOST_NAMES)
                    ?: throw IllegalStateException("Could not find a valid kafka host")

                log.info("$hostname is a valid host, assuming Kafka is running on this machine.")

                listOf(KafkaHostConfig(hostname))
            }

            val producerConfig = retrieveKafkaProducerConfiguration(hosts).also(config.producerConfigBody)
            val consumerConfig = retrieveConsumerConfig(hosts, ctx.serviceDescription).also(config.consumerConfigBody)

            log.info("Connected to Kafka")

            ctx.kafka = KafkaServicesImpl(
                consumerConfig,
                producerConfig,
                userConfig.defaultPartitions,
                userConfig.defaultReplicas
            )

            ctx.eventStreamService =
                KafkaStreamService(consumerConfig, producerConfig, Runtime.getRuntime().availableProcessors())
        }
    }

    companion object Feature : MicroFeatureFactory<KafkaFeature, KafkaFeatureConfiguration>,
        Loggable {
        override val key = MicroAttributeKey<KafkaFeature>("kafka-feature")
        override fun create(config: KafkaFeatureConfiguration): KafkaFeature =
            KafkaFeature(config)

        override val log = logger()

        internal val SERVICES_KEY = MicroAttributeKey<KafkaServices>("kafka-services")

        private val DEFAULT_HOST_NAMES = listOf(
            "kafka",
            "localhost"
        )
    }
}

data class KafkaUserConfig(
    val brokers: List<KafkaHostConfig>? = null,
    val defaultPartitions: Int = 32,
    val defaultReplicas: Short = 1
)

@Deprecated("Replace with new Kafka API")
var Micro.kafka: KafkaServices
    get() {
        requireFeature(KafkaFeature)
        return attributes[KafkaFeature.SERVICES_KEY]
    }
    internal set(value) {
        attributes[KafkaFeature.SERVICES_KEY] = value
    }

private val eventStreamServiceKey = MicroAttributeKey<EventStreamService>("event-stream-service")
var Micro.eventStreamService: EventStreamService
    get() {
        return attributes[eventStreamServiceKey]
    }
    set(value) {
        attributes[eventStreamServiceKey] = value
    }
