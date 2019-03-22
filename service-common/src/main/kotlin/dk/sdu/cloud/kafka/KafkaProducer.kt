package dk.sdu.cloud.kafka

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val DEBUG_VALUE_MAX_SIZE = 100

@Deprecated("Replace with new Kafka API")
class EventProducer<in K, in V>(
    private val producer: Producer<String, String>,
    private val description: StreamDescription<K, V>
) {
    val topicName: String get() = description.name

    suspend fun emit(key: K, value: V) = suspendCoroutine<RecordMetadata> { cont ->
        val stringKey = String(description.keySerde.serializer().serialize(description.name, key))
        val stringValue = String(description.valueSerde.serializer().serialize(description.name, value))

        log.debug(
            "Emitting event: [$topicName] $stringKey : ${stringValue.take(
                DEBUG_VALUE_MAX_SIZE
            )}"
        )
        producer.send(ProducerRecord(description.name, stringKey, stringValue)) { result, ex ->
            if (ex == null) cont.resume(result)
            else {
                log.warn("Caught exception while sending Kafka event to $topicName")
                log.warn(ex.stackTraceToString())

                cont.resumeWithException(ex)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

@Deprecated("Replace with new Kafka API")
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

@Deprecated("Replace with new Kafka API")
fun <K, V> Producer<String, String>.forStream(description: StreamDescription<K, V>): EventProducer<K, V> =
    EventProducer(this, description)

@Deprecated("Replace with new Kafka API")
fun <K, V> Producer<String, String>.forStream(
    description: MappedStreamDescription<K, V>
): MappedEventProducer<K, V> = MappedEventProducer(this, description)
