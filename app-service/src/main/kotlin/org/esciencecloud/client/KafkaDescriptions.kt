package org.esciencecloud.client

import org.apache.kafka.common.serialization.Serde
import org.esciencecloud.abc.services.defaultSerdeOrJson
import org.esciencecloud.kafka.StreamDescription
import org.esciencecloud.kafka.TableDescription
import org.slf4j.LoggerFactory

data class KafkaRequest<out EventType>(val header: RequestHeader, val event: EventType) {
    companion object {
        const val TYPE_PROPERTY = "type"
    }
}

data class RequestHeader(
        val uuid: String,
        val performedFor: ProxyClient
)

data class ProxyClient(val username: String, val password: String)

data class KafkaMappingDescription<in R : Any, K : Any, V : Any>(
        val topicName: String,
        val keySerde: Serde<K>,
        val valueSerde: Serde<V>,
        val mappper: (KafkaRequest<R>) -> Pair<K, V>
)

abstract class KafkaDescriptions {
    private val log = LoggerFactory.getLogger(javaClass)
    private val _descriptions: MutableList<KafkaMappingDescription<*, *, *>> = ArrayList()
    val descriptions: List<KafkaMappingDescription<*, *, *>> get() = _descriptions.toList()

    /**
     * Performs a registration on multiple endpoints that must all map to the same Kafka topic.
     *
     * The mapper code will be run at the API gateway and _not_ in the service.
     *
     * @see [KafkaCallDescription.mappedAtGateway]
     */
    inline fun <R : Any, reified K : Any, reified V : Any> KafkaCallDescriptionBundle<R>.mappedAtGateway(
            topicName: String,
            keySerde: Serde<K> = defaultSerdeOrJson(),
            valueSerde: Serde<V> = defaultSerdeOrJson(),
            noinline mapper: (KafkaRequest<R>) -> Pair<K, V>
    ) {
        forEach { it.mappedAtGateway(topicName, keySerde, valueSerde, mapper) }
    }

    /**
     * Registers how to map a GW REST request to a Kafka request.
     *
     *  This code will be run at the API gateway and _not_ in the service.
     */
    @Suppress("unused") // the receiver is used for type inference and type safety
    inline fun <R : Any, reified K : Any, reified V : Any> KafkaCallDescription<R>.mappedAtGateway(
            topicName: String,
            keySerde: Serde<K> = defaultSerdeOrJson(),
            valueSerde: Serde<V> = defaultSerdeOrJson(),
            noinline mapper: (KafkaRequest<R>) -> Pair<K, V>
    ): StreamDescription<K, V> {
        registerMapping(KafkaMappingDescription(topicName, keySerde, valueSerde, mapper))
        return stream(topicName, keySerde, valueSerde)
    }

    inline fun <reified K : Any, reified V : Any> stream(
            topicName: String,
            keySerde: Serde<K> = defaultSerdeOrJson(),
            valueSerde: Serde<V> = defaultSerdeOrJson()
    ): StreamDescription<K, V> {
        return StreamDescription(topicName, keySerde, valueSerde)
    }

    inline fun <reified K : Any, reified V : Any> table(
            topicName: String,
            keySerde: Serde<K> = defaultSerdeOrJson(),
            valueSerde: Serde<V> = defaultSerdeOrJson()
    ): TableDescription<K, V> {
        return TableDescription(topicName, keySerde, valueSerde)
    }

    fun registerMapping(description: KafkaMappingDescription<*, *, *>) {
        log.debug("Registering new Kafka descriptions $description")
        _descriptions.add(description)
    }
}