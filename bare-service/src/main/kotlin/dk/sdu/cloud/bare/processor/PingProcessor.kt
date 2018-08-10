package dk.sdu.cloud.bare.processor

import dk.sdu.cloud.bare.api.PingRequest
import org.apache.kafka.streams.kstream.KStream

class PingProcessor(
    private val stream: KStream<String, PingRequest>
) {
    fun configure() {
        stream.foreach { key, value ->
            println("Got a new ping request: $key - $value")
        }
    }
}