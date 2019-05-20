package dk.sdu.cloud.zenodo.api

import dk.sdu.cloud.events.EventStreamContainer

data class ZenodoPublishCommand(
    val jwt: String,
    val uuid: String,
    val publicationId: Long,
    val request: ZenodoPublishRequest
)

object ZenodoCommandStreams : EventStreamContainer() {
    val publishCommands = stream<ZenodoPublishCommand>("zenodoPublish", { it.uuid })
}
