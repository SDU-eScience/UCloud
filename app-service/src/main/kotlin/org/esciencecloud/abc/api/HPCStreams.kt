package org.esciencecloud.abc.api

import org.esciencecloud.service.KafkaDescriptions

object HPCStreams : KafkaDescriptions() {
    init {
        HPCApplicationDescriptions // Force initialization of dependencies here
    }

    val AppRequests = HPCApplicationDescriptions.AppRequest.descriptions.mappedAtGateway("request.hpcApp") {
        Pair(it.header.uuid, it)
    }
    val AppEvents = stream<String, HPCAppEvent>("hpcAppEvents")

    // TODO We can clean up in these
    val JobIdToApp = table<String, HPCAppEvent.Pending>("hpcJobToApp")
    val SlurmIdToJobId = table<Long, String>("hpcSlurmIdToJobId")
    val JobIdToStatus = table<String, HPCAppEvent>("hpcJobToStatus")

    val RecentlyCompletedJobs = table<String, MyJobs>("hpcRecentJobs")
}