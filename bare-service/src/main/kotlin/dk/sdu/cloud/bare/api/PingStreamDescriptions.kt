package dk.sdu.cloud.bare.api

import dk.sdu.cloud.service.KafkaDescriptions

object PingStreamDescriptions : KafkaDescriptions() {
    val stream = stream<String, PingRequest>("ping-stream") { it.ping }
}