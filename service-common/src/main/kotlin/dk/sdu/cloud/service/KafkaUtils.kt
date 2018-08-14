package dk.sdu.cloud.service

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.Consumed
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.KeyValueStore
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


