package dk.sdu.cloud.service

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.LoggerFactory
import kotlin.coroutines.experimental.suspendCoroutine
import kotlin.math.min

private const val MAX_INDEX = 100

class EventProducer<in K, in V>(
    private val producer: KafkaProducer<String, String>,
    private val description: StreamDescription<K, V>
) {
    val topicName: String get() = description.name

    suspend fun emit(key: K, value: V) = suspendCoroutine<RecordMetadata> { cont ->
        val stringKey = String(description.keySerde.serializer().serialize(description.name, key))
        val stringValue = String(description.valueSerde.serializer().serialize(description.name, value))

        log.debug("Emitting event: $stringKey : ${stringValue.substring(0, min(MAX_INDEX, stringValue.length))}")
        producer.send(ProducerRecord(description.name, stringKey, stringValue)) { result, ex ->
            if (ex == null) cont.resume(result)
            else cont.resumeWithException(ex)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventProducer::class.java)
    }
}

class MappedEventProducer<in K, in V>(
    producer: KafkaProducer<String, String>,
    private val description: MappedStreamDescription<K, V>
) {
    private val delegate = EventProducer(producer, description)

    suspend fun emit(value: V) {
        val key = description.mapper(value)
        delegate.emit(key, value)
    }
}

fun <K, V> KafkaProducer<String, String>.forStream(description: StreamDescription<K, V>): EventProducer<K, V> =
    EventProducer(this, description)

fun <K, V> KafkaProducer<String, String>.forStream(
    description: MappedStreamDescription<K, V>
): MappedEventProducer<K, V> = MappedEventProducer(this, description)
