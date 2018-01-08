package dk.sdu.cloud.service

import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.Consumed
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KGroupedStream
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Serialized
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.StreamsMetadata
import dk.sdu.cloud.client.KafkaCallDescription
import dk.sdu.cloud.client.KafkaCallDescriptionBundle
import dk.sdu.cloud.service.JsonSerde.jsonSerde
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

interface StreamDescription<K, V> {
    val name: String
    val keySerde: Serde<K>
    val valueSerde: Serde<V>

    fun stream(builder: StreamsBuilder): KStream<K, V> =
        builder.stream(name, Consumed.with(keySerde, valueSerde))
}

class SimpleStreamDescription<Key, Value>(
        override val name: String,
        override val keySerde: Serde<Key>,
        override val valueSerde: Serde<Value>
) : StreamDescription<Key, Value> {
    fun groupByKey(builder: StreamsBuilder): KGroupedStream<Key, Value> =
            stream(builder).groupByKey(Serialized.with(keySerde, valueSerde))
}

class MappedStreamDescription<K, V>(
        override val name: String,
        override val keySerde: Serde<K>,
        override val valueSerde: Serde<V>,
        val mapper: (V) -> K
) : StreamDescription<K, V>

class TableDescription<Key, Value>(val name: String, val keySerde: Serde<Key>, val valueSerde: Serde<Value>) {
    fun findStreamMetadata(streams: KafkaStreams, key: Key): StreamsMetadata {
        return streams.metadataForKey(name, key, keySerde.serializer())
    }

    fun localKeyValueStore(streams: KafkaStreams): ReadOnlyKeyValueStore<Key, Value> =
            streams.store(name, QueryableStoreTypes.keyValueStore<Key, Value>())
}

data class KafkaRequest<out EventType>(val header: RequestHeader, val event: EventType) {
    companion object {
        const val TYPE_PROPERTY = "type"
    }
}

data class RequestHeader(
        val uuid: String,
        val performedFor: RawAuthToken
)

typealias RawAuthToken = String

class KafkaMappingDescription<R : Any, K : Any, V : Any>(
        val topicName: String,
        val targets: KafkaCallDescriptionBundle<R>,
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
    ): StreamDescription<K, V> {
        registerMapping(KafkaMappingDescription(topicName, this, keySerde, valueSerde, mapper))
        return stream(topicName, keySerde, valueSerde)
    }

    /**
     * Registers how to map a GW REST request to a Kafka request.
     *
     *  This code will be run at the API gateway and _not_ in the service.
     */
    inline fun <R : Any, reified K : Any, reified V : Any> KafkaCallDescription<R>.mappedAtGateway(
            topicName: String,
            keySerde: Serde<K> = defaultSerdeOrJson(),
            valueSerde: Serde<V> = defaultSerdeOrJson(),
            noinline mapper: (KafkaRequest<R>) -> Pair<K, V>
    ): StreamDescription<K, V> {
        registerMapping(KafkaMappingDescription(topicName, listOf(this), keySerde, valueSerde, mapper))
        return stream(topicName, keySerde, valueSerde)
    }

    inline fun <reified K : Any, reified V : Any> stream(
            topicName: String,
            keySerde: Serde<K> = defaultSerdeOrJson(),
            valueSerde: Serde<V> = defaultSerdeOrJson()
    ): StreamDescription<K, V> {
        return SimpleStreamDescription(topicName, keySerde, valueSerde)
    }

    inline fun <reified K : Any, reified V : Any> stream(
            topicName: String,
            keySerde: Serde<K> = defaultSerdeOrJson(),
            valueSerde: Serde<V> = defaultSerdeOrJson(),
            noinline keyMapper: (V) -> K
    ): MappedStreamDescription<K, V> {
        return MappedStreamDescription(topicName, keySerde, valueSerde, keyMapper)
    }

    inline fun <reified K : Any, reified V : Any> table(
            topicName: String,
            keySerde: Serde<K> = defaultSerdeOrJson(),
            valueSerde: Serde<V> = defaultSerdeOrJson()
    ): TableDescription<K, V> {
        return TableDescription(topicName, keySerde, valueSerde)
    }

    fun registerMapping(description: KafkaMappingDescription<*, *, *>) {
        log.debug("Registering new Kafka descriptions ${description.topicName}")
        _descriptions.add(description)
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified Type : Any> defaultSerdeOrJson(): Serde<Type> = when (Type::class) {
    String::class -> Serdes.String() as Serde<Type>
    Double::class -> Serdes.Double() as Serde<Type>
    Int::class -> Serdes.Integer() as Serde<Type>
    Float::class -> Serdes.Float() as Serde<Type>
    Short::class -> Serdes.Short() as Serde<Type>
    ByteArray::class -> Serdes.ByteArray() as Serde<Type>
    ByteBuffer::class -> Serdes.ByteBuffer() as Serde<Type>
    Bytes::class -> Serdes.Bytes() as Serde<Type>

    else -> jsonSerde()
}
