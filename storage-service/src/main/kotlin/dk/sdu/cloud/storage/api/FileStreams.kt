package dk.sdu.cloud.storage.api

import dk.sdu.cloud.service.KafkaDescriptions

object FileStreams : KafkaDescriptions() {
    val favoriteUpdateStream = FileDescriptions.favoriteBundle.mappedAtGateway("request.fileFavorite") {
        it.event.path to it
    }
}