package dk.sdu.cloud.auth.api

import dk.sdu.cloud.kafka.KafkaDescriptions
import dk.sdu.cloud.kafka.MappedEventProducer
import org.apache.kafka.streams.kstream.KStream

typealias UserEventProducer = MappedEventProducer<String, UserEvent>
typealias UserEventConsumer = KStream<String, UserEvent>

object AuthStreams : KafkaDescriptions() {
    val UserUpdateStream = stream<String, UserEvent>("auth.user") { it.key }
}
