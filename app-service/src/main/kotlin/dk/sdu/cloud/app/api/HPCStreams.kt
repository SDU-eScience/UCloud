package dk.sdu.cloud.app.api

import dk.sdu.cloud.service.KafkaDescriptions
import dk.sdu.cloud.service.KafkaRequest

object HPCStreams : KafkaDescriptions() {
    val AppRequests = stream<String, KafkaRequest<AppRequest>>("request.hpcApp") {
        it.header.uuid
    }

    /**
     * An event stream of [HPCAppEvent]. The events use the slurm ID for their keys.
     */
    val AppEvents = stream<String, HPCAppEvent>("hpcAppEvents")
}