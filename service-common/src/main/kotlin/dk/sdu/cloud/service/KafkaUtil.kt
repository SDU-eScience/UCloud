package dk.sdu.cloud.service

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.Consumed
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.KeyValueStore
import java.io.File
import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine
import kotlin.reflect.KClass

// TODO Use branch instead
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
    suspend fun emit(key: K, value: V) = suspendCoroutine<RecordMetadata> { cont ->
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

data class DivergedStream<K, V>(val predicateTrue: KStream<K, V>, val predicateFalse: KStream<K, V>)

object KafkaUtil {
    fun retrieveKafkaStreamsConfiguration(config: ConnectionConfig): Properties {
        return retrieveKafkaStreamsConfiguration(config.kafka, config.service)
    }

    fun retrieveKafkaStreamsConfiguration(
            kafkaConnectionConfig: KafkaConnectionConfig,
            serviceConfig: ServiceConnectionConfig
    ): Properties = Properties().apply {
        this[StreamsConfig.APPLICATION_ID_CONFIG] = serviceConfig.description.name
        this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConnectionConfig.servers.joinToString(",") { it.toString() }
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events
        this[StreamsConfig.APPLICATION_SERVER_CONFIG] = "${serviceConfig.hostname}:${serviceConfig.port}"

        // The defaults do not use java.io.tmpdir
        this[StreamsConfig.STATE_DIR_CONFIG] = File(System.getProperty("java.io.tmpdir"), "kafka-streams").absolutePath
    }

    fun retrieveKafkaProducerConfiguration(config: ConnectionConfig): Properties {
        return retrieveKafkaProducerConfiguration(config.kafka)
    }

    fun retrieveKafkaProducerConfiguration(kafkaServers: KafkaConnectionConfig): Properties = Properties().apply {
        this[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaServers.servers.joinToString(",") { it.toString() }
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
    }
}
