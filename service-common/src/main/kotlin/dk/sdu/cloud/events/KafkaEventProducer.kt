package dk.sdu.cloud.events

import dk.sdu.cloud.defaultMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class KafkaEventProducer<V : Any>(
    private val kafkaProducerConfig: Properties,
    override val stream: EventStream<V>
) : EventProducer<V> {
    private val producer = KafkaProducer<String, String>(kafkaProducerConfig)
    private val writer = defaultMapper.writerFor(stream.typeReference)

    override suspend fun produce(events: List<V>) {
        events.forEach {
            val key = stream.keySelector(it)
            val value = writer.writeValueAsString(it)

            producer.aSend(stream.name, key, value)
        }
    }

    private suspend fun KafkaProducer<String, String>.aSend(topic: String, key: String, value: String) =
        suspendCoroutine<RecordMetadata> { cont ->
            send(ProducerRecord(topic, key, value)) { result, ex ->
                if (ex == null) cont.resume(result)
                else {
                    cont.resumeWithException(ex)
                }
            }
        }
}
