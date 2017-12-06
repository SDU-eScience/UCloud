package org.esciencecloud.abc.util

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KGroupedStream
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.kstream.KTable
import org.esciencecloud.client.KafkaRequest
import org.esciencecloud.client.RequestHeader
import org.esciencecloud.kafka.StreamDescription
import org.esciencecloud.kafka.TableDescription
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.Error
import kotlin.coroutines.experimental.suspendCoroutine
import kotlin.reflect.KClass

fun <K, V> KStream<K, V>.diverge(predicate: (K, V) -> Boolean): DivergedStream<K, V> {
    // We only want to run the predicate once for every item
    val predicateStream = map { key, value -> KeyValue(key, Pair(predicate(key, value), value)) }
    val predicateTrue = predicateStream.filter { _, value -> value.first }.mapValues { it.second }
    val predicateFalse = predicateStream.filter { _, value -> !value.first }.mapValues { it.second }

    return DivergedStream(predicateTrue, predicateFalse)
}

class EventProducer<in K, in V>(
        private val producer: KafkaProducer<String, String>,
        private val description: StreamDescription<K, V>
) {
    suspend fun emit(key: K, value: V) = suspendCoroutine<RecordMetadata>{ cont ->
        val stringKey = String(description.keySerde.serializer().serialize(description.name, key))
        val stringValue = String(description.valueSerde.serializer().serialize(description.name, value))

        producer.send(ProducerRecord(description.name, stringKey, stringValue)) { result, ex ->
            if (ex == null) cont.resume(result)
            else cont.resumeWithException(ex)
        }
    }
}

fun <K, V> KafkaProducer<String, String>.forStream(description: StreamDescription<K, V>): EventProducer<K, V> =
        EventProducer(this, description)

fun <K, V> KStreamBuilder.stream(description: StreamDescription<K, V>): KStream<K, V> =
        stream(description.keySerde, description.valueSerde, description.name)

fun <K, V> KStreamBuilder.table(description: StreamDescription<K, V>): KTable<K, V> =
        table(description.keySerde, description.valueSerde, description.name)

fun <K, V, A> KStreamBuilder.aggregate(
        description: StreamDescription<K, V>,
        tableDescription: TableDescription<K, A>,
        initializer: () -> A? = { null },
        aggregate: (K, V, A?) -> A
): KTable<K, A> {
    return stream(description).groupByKey(description.keySerde, description.valueSerde).aggregate(
            initializer,
            aggregate,
            tableDescription.valueSerde,
            tableDescription.name
    )
}

fun <K, V, A> KGroupedStream<K, V>.aggregate(
        target: TableDescription<K, A>,
        initializer: () -> A? = { null },
        aggregate: (K, V, A?) -> A
) {
    aggregate(initializer, aggregate, target.valueSerde, target.name)
}

fun <K, V : Any, R : V> KStream<K, V>.filterIsInstance(klass: KClass<R>) =
        filter { _, value -> klass.isInstance(value) }.mapValues {
            @Suppress("UNCHECKED_CAST")
            it as R
        }

fun <K, V> KStream<K, V>.toTable(): KTable<K, V> = groupByKey().reduce { _, newValue -> newValue }
fun <K, V> KStream<K, V>.toTable(keySerde: Serde<K>, valSerde: Serde<V>): KTable<K, V> =
        groupByKey(keySerde, valSerde).reduce { _, newValue -> newValue }

fun <K, V> KStream<K, V>.through(description: StreamDescription<K, V>): KStream<K, V> =
    through(description.keySerde, description.valueSerde, description.name)

fun <K, V> KStream<K, V>.to(description: StreamDescription<K, V>) {
    to(description.keySerde, description.valueSerde, description.name)
}

// Not a pretty name, but I couldn't find any name indicating that authentication and taken place that didn't
// also imply that it was successful.
sealed class RequestAfterAuthentication<out T> {
    abstract val originalRequest: KafkaRequest<T>

    val event: T
        get() = originalRequest.event

    val header: RequestHeader
        get() = originalRequest.header

    class Authenticated<out T>(
            override val originalRequest: KafkaRequest<T>,
            val connection: StorageConnection
    ) : RequestAfterAuthentication<T>()

    class Unauthenticated<out T>(
            override val originalRequest: KafkaRequest<T>,
            val error: Error<Any>
    ) : RequestAfterAuthentication<T>()
}

data class DivergedStream<K, V>(val predicateTrue: KStream<K, V>, val predicateFalse: KStream<K, V>)
