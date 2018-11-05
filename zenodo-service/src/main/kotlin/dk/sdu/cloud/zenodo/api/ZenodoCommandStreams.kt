package dk.sdu.cloud.zenodo.api

import dk.sdu.cloud.service.KafkaDescriptions

data class ZenodoPublishCommand(
    val jwt: String,
    val uuid: String,
    val publicationId: Long,
    val request: ZenodoPublishRequest
)

object ZenodoCommandStreams : KafkaDescriptions() {
    val publishCommands = stream<String, ZenodoPublishCommand>("zenodoPublish") { it.uuid }
}
