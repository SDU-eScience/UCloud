package dk.sdu.cloud.app.api

import dk.sdu.cloud.service.KafkaDescriptions

object HPCStreams : KafkaDescriptions() {
    val AppRequests = HPCJobDescriptions.appRequestBundle.mappedAtGateway("request.hpcApp") {
        Pair(it.header.uuid, it)
    }

    /**
     * An event stream of [HPCAppEvent]. The events use the slurm ID for their keys.
     */
    val AppEvents = stream<String, HPCAppEvent>("hpcAppEvents")
}