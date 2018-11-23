package dk.sdu.cloud.service

import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val MAX_INDEX = 100

class EventProducer<in K, in V>(
    private val producer: Producer<String, String>,
    private val description: StreamDescription<K, V>
) {
    val topicName: String get() = description.name

    suspend fun emit(key: K, value: V) = suspendCoroutine<RecordMetadata> { cont ->
        val stringKey = String(description.keySerde.serializer().serialize(description.name, key))
        val stringValue = String(description.valueSerde.serializer().serialize(description.name, value))

        log.debug("Emitting event: [$topicName] $stringKey : ${stringValue.take(MAX_INDEX)}")
        producer.send(ProducerRecord(description.name, stringKey, stringValue)) { result, ex ->
            if (ex == null) cont.resume(result)
            else cont.resumeWithException(ex)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

class MappedEventProducer<in K, in V>(
    producer: Producer<String, String>,
    private val description: MappedStreamDescription<K, V>
) {
    private val delegate = EventProducer(producer, description)

    suspend fun emit(value: V) {
        val key = description.mapper(value)
        delegate.emit(key, value)
    }
}

fun <K, V> Producer<String, String>.forStream(description: StreamDescription<K, V>): EventProducer<K, V> =
    EventProducer(this, description)

fun <K, V> Producer<String, String>.forStream(
    description: MappedStreamDescription<K, V>
): MappedEventProducer<K, V> = MappedEventProducer(this, description)
