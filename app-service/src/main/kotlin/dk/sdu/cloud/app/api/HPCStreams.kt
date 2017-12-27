package dk.sdu.cloud.app.api

import dk.sdu.cloud.service.KafkaDescriptions

object HPCStreams : KafkaDescriptions() {
    val AppRequests = HPCJobDescriptions.appRequestBundle.mappedAtGateway("request.hpcApp") {
        Pair(it.header.uuid, it)
    }
    val AppEvents = stream<String, HPCAppEvent>("hpcAppEvents")

    // TODO We can clean up in these
    val JobIdToApp = table<String, HPCAppEvent.Pending>("hpcJobToApp")
    val SlurmIdToJobId = table<Long, String>("hpcSlurmIdToJobId")
    val JobIdToStatus = table<String, HPCAppEvent>("hpcJobToStatus")

    val RecentlyCompletedJobs = table<String, MyJobs>("hpcRecentJobs")
}