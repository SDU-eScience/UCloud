package dk.sdu.cloud.tus.api

import dk.sdu.cloud.service.KafkaDescriptions
import dk.sdu.cloud.service.MappedEventProducer
import org.apache.kafka.streams.kstream.KStream

typealias UploadEventStream = KStream<String, TusUploadEvent>
typealias UploadEventProducer = MappedEventProducer<String, TusUploadEvent>

object TusStreams : KafkaDescriptions() {
    val UploadEvents = stream<String, TusUploadEvent>("uploadEvents") { it.id }
}