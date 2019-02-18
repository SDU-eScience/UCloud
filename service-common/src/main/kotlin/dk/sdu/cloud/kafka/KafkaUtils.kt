package dk.sdu.cloud.kafka

import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import kotlin.reflect.KClass

fun <K, V> StreamsBuilder.stream(description: StreamDescription<K, V>): KStream<K, V> =
    stream(description.name, Consumed.with(description.keySerde, description.valueSerde))

fun <K, V : Any, R : V> KStream<K, V>.filterIsInstance(klass: KClass<R>) =
    filter { _, value -> klass.isInstance(value) }.mapValues { _, value ->
        @Suppress("UNCHECKED_CAST")
        value as R
    }

fun <K, V> KStream<K, V>.to(description: StreamDescription<K, V>) {
    to(description.name, Produced.with(description.keySerde, description.valueSerde))
}


