package dk.sdu.cloud.micro

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.server.auditStream
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.kafka.KafkaDescriptions
import dk.sdu.cloud.kafka.StreamDescription
import dk.sdu.cloud.service.ClassDiscovery
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

class KafkaTopicFeatureConfiguration(
    val discoverDefaults: Boolean = true,
    val basePackages: List<String> = emptyList()
)

private data class SimpleEventStream<V : Any>(
    override val name: String,
    override val desiredPartitions: Int?,
    override val desiredReplicas: Short?,
    override val keySelector: (V) -> String
) : EventStream<V> {
    override fun deserialize(value: String): V {
        throw IllegalStateException()
    }

    override fun serialize(event: V): String {
        throw IllegalStateException()
    }
}

class KafkaTopicFeature(
    private val config: KafkaTopicFeatureConfiguration
) : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(KafkaFeature)
        val eventStreamService = ctx.eventStreamService

        val allStreams = runDetection(ctx, listOf(legacyDetection, newDetection))

        val topicNames = allStreams.map { it.value.name }
        if (log.isDebugEnabled) {
            log.debug("Discovered the following topic names:")
            topicNames.forEach { log.debug("  $it") }
        }

        eventStreamService.createStreams(allStreams.values.toList())
        log.info("Kafka topics are ready!")
    }

    interface Handler {
        fun inspectClass(klass: KClass<*>)
        fun retrieveResults(): Map<String, EventStream<*>>
    }

    private fun runDetection(ctx: Micro, handlers: List<Handler>): HashMap<String, EventStream<*>> {
        val packages = ArrayList<String>().apply {
            addAll(config.basePackages)

            if (config.discoverDefaults) {
                val pkgName = ctx.serviceDescription.name.replace('-', '.').replace('_', '.')
                add("dk.sdu.cloud.$pkgName.api")
            }
        }.toList()

        val discoverer = ClassDiscovery(packages, javaClass.classLoader) { klass ->
            handlers.forEach { it.inspectClass(klass) }
        }

        runBlocking { discoverer.detect() }

        val result = HashMap<String, EventStream<*>>()
        handlers.forEach { result.putAll(it.retrieveResults()) }
        return result
    }

    private val legacyDetection = object : Handler {
        val allStreams = HashMap<String, StreamDescription<*, *>>()
        override fun inspectClass(klass: KClass<*>) {
            val objectInstance = klass.objectInstance
            if (objectInstance != null && objectInstance is KafkaDescriptions) {
                log.debug("Retrieving streams from $klass")
                objectInstance.streams.forEach { allStreams[it.name] = it }
            }
        }

        override fun retrieveResults(): Map<String, EventStream<*>> {
            // The important thing is that this delivers name, partitions, and replicas.
            return allStreams.mapValues {
                SimpleEventStream<Any>(
                    it.value.name,
                    it.value.desiredPartitions,
                    it.value.desiredReplicas,
                    { it.toString() }
                )
            }
        }
    }

    private val newDetection = object : Handler {
        val allStreams = HashMap<String, EventStream<*>>()
        override fun inspectClass(klass: KClass<*>) {
            val objectInstance = klass.objectInstance
            if (objectInstance != null && objectInstance is EventStreamContainer) {
                log.debug("Retrieving streams from $klass")
                objectInstance.streams.forEach { allStreams[it.name] = it }
            }

            if (objectInstance != null && objectInstance is CallDescriptionContainer) {
                val auditStream = objectInstance.auditStream
                allStreams[auditStream.name] = auditStream
            }
        }

        override fun retrieveResults(): Map<String, EventStream<*>> {
            return allStreams
        }
    }

    companion object Feature : MicroFeatureFactory<KafkaTopicFeature, KafkaTopicFeatureConfiguration>,
        Loggable {
        override val log = logger()
        override val key: MicroAttributeKey<KafkaTopicFeature> =
            MicroAttributeKey("kafka-topic-feature")

        override fun create(config: KafkaTopicFeatureConfiguration): KafkaTopicFeature {
            return KafkaTopicFeature(config)
        }
    }
}
