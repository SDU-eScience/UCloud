package dk.sdu.cloud.service

import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.ServiceDescription
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.admin.Config
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.NewPartitions
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException

class KafkaTopicFeatureConfiguration(
    val discoverDefaults: Boolean = true,
    val basePackages: List<String> = emptyList()
)

class KafkaTopicFeature(
    private val config: KafkaTopicFeatureConfiguration
) : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(KafkaFeature)
        val kafka = ctx.kafka

        val packages = ArrayList<String>().apply {
            addAll(config.basePackages)

            if (config.discoverDefaults) {
                val pkgName = ctx.serviceDescription.name.replace('-', '.').replace('_', '.')
                add("dk.sdu.cloud.$pkgName.api")
            }
        }.toList()

        val allStreams = ArrayList<StreamDescription<*, *>>()
        val discoverer = ClassDiscovery(packages, javaClass.classLoader) { klass ->
            val objectInstance = klass.objectInstance
            if (objectInstance != null && objectInstance is KafkaDescriptions) {
                log.debug("Retrieving streams from $klass")
                allStreams.addAll(objectInstance.streams)
            }

            if (objectInstance != null && objectInstance is RESTDescriptions) {
                allStreams.add(objectInstance.auditStream)
            }
        }

        runBlocking { discoverer.detect() }

        val topicNames = allStreams.map { it.name }
        if (log.isDebugEnabled) {
            log.debug("Discovered the following topic names:")
            topicNames.forEach { log.debug("  $it") }
        }

        val describeFuture = kafka.adminClient.describeTopics(topicNames)
        val missingTopics = describeFuture.values().mapNotNull { (topicName, future) ->
            try {
                val topicDescription = future.get()
                log.debug("Got back the following result: $topicDescription")
                log.debug(topicDescription.name())
                null
            } catch (ex: Exception) {
                if (ex.cause is UnknownTopicOrPartitionException) {
                    topicName
                } else {
                    log.debug("Caught exception for $topicName")
                    log.debug(ex.stackTraceToString())
                    throw ex
                }
            }
        }

        if (log.isDebugEnabled) {
            log.debug("The following topics were missing, we are creating them now:")
            missingTopics.forEach { log.debug("  $it") }
        }

        val newTopicCommands = missingTopics.map { topic ->
            NewTopic(topic, kafka.defaultPartitions, kafka.defaultReplicas)
        }

        kafka.adminClient.createTopics(newTopicCommands).all().get()

        log.info("Kafka topics are ready!")
    }

    companion object Feature : MicroFeatureFactory<KafkaTopicFeature, KafkaTopicFeatureConfiguration>, Loggable {
        override val log = logger()
        override val key: MicroAttributeKey<KafkaTopicFeature> = MicroAttributeKey("kafka-topic-feature")

        override fun create(config: KafkaTopicFeatureConfiguration): KafkaTopicFeature {
            return KafkaTopicFeature(config)
        }
    }
}
