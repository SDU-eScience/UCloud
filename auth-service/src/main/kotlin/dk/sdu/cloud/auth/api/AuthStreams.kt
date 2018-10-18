package dk.sdu.cloud.auth.api

import dk.sdu.cloud.service.KafkaDescriptions
import dk.sdu.cloud.service.MappedEventProducer
import org.apache.kafka.streams.kstream.KStream

typealias UserEventProducer = MappedEventProducer<String, UserEvent>
typealias UserEventConsumer = KStream<String, UserEvent>

object AuthStreams : KafkaDescriptions() {
    val UserUpdateStream = stream<String, UserEvent>("auth.user") { it.key }
}
