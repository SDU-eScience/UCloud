package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.events.KafkaStreamService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.findValidHostname
import org.apache.kafka.clients.consumer.ConsumerConfig
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

class KafkaFeatureConfiguration(
    internal val consumerConfigBody: (Properties) -> Unit = {},
    internal val producerConfigBody: (Properties) -> Unit = {},
    val parallelism: Int = Runtime.getRuntime().availableProcessors()
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


        ctx.eventStreamService =
            KafkaStreamService(consumerConfig, producerConfig, config.parallelism)
    }

    companion object Feature : MicroFeatureFactory<KafkaFeature, KafkaFeatureConfiguration>,
        Loggable {
        override val key = MicroAttributeKey<KafkaFeature>("kafka-feature")
        override fun create(config: KafkaFeatureConfiguration): KafkaFeature =
            KafkaFeature(config)

        override val log = logger()

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

private val eventStreamServiceKey = MicroAttributeKey<EventStreamService>("event-stream-service")
var Micro.eventStreamService: EventStreamService
    get() {
        return attributes[eventStreamServiceKey]
    }
    set(value) {
        attributes[eventStreamServiceKey] = value
    }
