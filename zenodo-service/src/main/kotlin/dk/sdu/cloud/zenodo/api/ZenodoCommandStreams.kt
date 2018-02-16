package dk.sdu.cloud.zenodo.api

import dk.sdu.cloud.service.KafkaDescriptions

object ZenodoCommandStreams : KafkaDescriptions() {
    val publishCommands = ZenodoDescriptions.publish.mappedAtGateway("test") {
        it.header.uuid to it
    }
}