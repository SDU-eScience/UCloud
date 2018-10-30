package dk.sdu.cloud.service

import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KGroupedStream
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KTable
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.Serialized
import org.apache.kafka.streams.state.KeyValueStore
import kotlin.reflect.KClass

fun <K, V> StreamsBuilder.stream(description: StreamDescription<K, V>): KStream<K, V> =
    stream(description.name, Consumed.with(description.keySerde, description.valueSerde))

@Deprecated(message = "No longer in use")
fun <K, V> StreamsBuilder.table(description: StreamDescription<K, V>): KTable<K, V> =
    table(description.name, Consumed.with(description.keySerde, description.valueSerde))

@Deprecated(message = "No longer in use")
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

@Deprecated(message = "No longer in use")
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
    filter { _, value -> klass.isInstance(value) }.mapValues { _, value ->
        @Suppress("UNCHECKED_CAST")
        value as R
    }

@Deprecated(message = "No longer in use")
fun <K, V> KStream<K, V>.toTable(): KTable<K, V> = groupByKey().reduce { _, newValue -> newValue }

@Deprecated(message = "No longer in use")
fun <K, V> KStream<K, V>.toTable(keySerde: Serde<K>, valSerde: Serde<V>): KTable<K, V> =
    groupByKey(Serialized.with(keySerde, valSerde)).reduce { _, newValue -> newValue }

@Deprecated(message = "No longer in use")
fun <K, V> KStream<K, V>.through(description: StreamDescription<K, V>): KStream<K, V> =
    through(description.name, Produced.with(description.keySerde, description.valueSerde))

fun <K, V> KStream<K, V>.to(description: StreamDescription<K, V>) {
    to(description.name, Produced.with(description.keySerde, description.valueSerde))
}


