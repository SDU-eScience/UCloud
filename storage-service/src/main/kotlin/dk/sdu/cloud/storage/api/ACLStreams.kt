package dk.sdu.cloud.storage.api

import dk.sdu.cloud.service.KafkaDescriptions

object ACLStreams : KafkaDescriptions() {
    val aclUpdates = ACLDescriptions.grantRights.mappedAtGateway("aclUpdates") { it.event.onFile to it }
}