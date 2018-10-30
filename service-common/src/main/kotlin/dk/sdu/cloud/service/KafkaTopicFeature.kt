package dk.sdu.cloud.service

import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.ServiceDescription
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.admin.NewPartitions
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.TopicDescription
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

        val allStreams = findStreams(ctx)

        val topicNames = allStreams.map { it.value.name }
        if (log.isDebugEnabled) {
            log.debug("Discovered the following topic names:")
            topicNames.forEach { log.debug("  $it") }
        }

        val relevantTopics = findRelevantTopics(kafka, topicNames)
        createMissingTopics(relevantTopics, kafka)
        createMissingPartitions(relevantTopics, allStreams, kafka)

        log.info("Kafka topics are ready!")
    }

    private fun findStreams(ctx: Micro): HashMap<String, StreamDescription<*, *>> {
        val packages = ArrayList<String>().apply {
            addAll(config.basePackages)

            if (config.discoverDefaults) {
                val pkgName = ctx.serviceDescription.name.replace('-', '.').replace('_', '.')
                add("dk.sdu.cloud.$pkgName.api")
            }
        }.toList()

        val allStreams = HashMap<String, StreamDescription<*, *>>()
        val discoverer = ClassDiscovery(packages, javaClass.classLoader) { klass ->
            val objectInstance = klass.objectInstance
            if (objectInstance != null && objectInstance is KafkaDescriptions) {
                log.debug("Retrieving streams from $klass")
                objectInstance.streams.forEach { allStreams[it.name] = it }
            }

            if (objectInstance != null && objectInstance is RESTDescriptions) {
                val auditStream = objectInstance.auditStream
                allStreams[auditStream.name] = auditStream
            }
        }

        runBlocking { discoverer.detect() }
        return allStreams
    }

    private fun findRelevantTopics(
        kafka: KafkaServices,
        topicNames: List<String>
    ): Map<String, TopicDescription?> {
        val describeFuture = kafka.adminClient.describeTopics(topicNames)
        return describeFuture.values().map { (topicName, future) ->
            try {
                val topicDescription = future.get()
                log.debug("Got back the following result: $topicDescription")
                log.debug(topicDescription.name())
                topicName to topicDescription
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

    private fun createMissingTopics(
        existingTopics: Map<String, TopicDescription?>,
        kafka: KafkaServices
    ) {
        val missingTopics = existingTopics.filterValues { it == null }.keys
        if (log.isDebugEnabled && missingTopics.isNotEmpty()) {
            log.debug("The following topics were missing, we are creating them now:")
            missingTopics.forEach { log.debug("  $it") }
        }

        val newTopicCommands = missingTopics.map { topic ->
            NewTopic(topic, kafka.defaultPartitions, kafka.defaultReplicas)
        }

        kafka.adminClient.createTopics(newTopicCommands).all().get()
    }

    private fun createMissingPartitions(
        existingTopics: Map<String, TopicDescription?>,
        allStreams: HashMap<String, StreamDescription<*, *>>,
        kafka: KafkaServices
    ) {
        val createPartitionCommand: Map<String, NewPartitions> =
            existingTopics.values.asSequence().filterNotNull().mapNotNull {
                val stream = allStreams[it.name()] ?: return@mapNotNull null
                val numPartitions = it.partitions().size
                val desiredPartitions = stream.desiredPartitions ?: kafka.defaultPartitions

                when {
                    desiredPartitions > numPartitions -> {
                        log.info(
                            "Increasing number of partitions from $desiredPartitions to $numPartitions for topic " +
                                    "'${stream.name}'"
                        )

                        stream.name to NewPartitions.increaseTo(desiredPartitions)
                    }

                    desiredPartitions < numPartitions -> {
                        log.warn(
                            "The desired number of partitions for topic '${stream.name}' is lower than actual number of " +
                                    "partitions ($numPartitions > $desiredPartitions). It is not possible to migrate this " +
                                    "automatically. Manual migration is needed."
                        )
                        null
                    }

                    else -> null
                }
            }.toMap()

        if (createPartitionCommand.isNotEmpty()) {
            log.info("Creating new partitions")
            log.info(createPartitionCommand.toString())

            kafka.adminClient.createPartitions(createPartitionCommand).all().get()
        }
    }

    companion object Feature : MicroFeatureFactory<KafkaTopicFeature, KafkaTopicFeatureConfiguration>, Loggable {
        override val log = logger()
        override val key: MicroAttributeKey<KafkaTopicFeature> = MicroAttributeKey("kafka-topic-feature")

        override fun create(config: KafkaTopicFeatureConfiguration): KafkaTopicFeature {
            return KafkaTopicFeature(config)
        }
    }
}
