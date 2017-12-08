package org.esciencecloud.abc.api

import org.esciencecloud.service.KafkaDescriptions
import org.esciencecloud.abc.api.HPCApplicationDescriptions.AppRequest.*

object HPCStreams : KafkaDescriptions() {
    val AppRequests = listOf(Start.description, Cancel.description).mappedAtGateway("request.hpcApp") {
        Pair(it.header.uuid, it)
    }
    val AppEvents = stream<String, HPCAppEvent>("hpcAppEvents")

    // TODO We can clean up in these
    val JobIdToApp = table<String, HPCAppEvent.Pending>("hpcJobToApp")
    val SlurmIdToJobId = table<Long, String>("hpcSlurmIdToJobId")
    val JobIdToStatus = table<String, HPCAppEvent>("hpcJobToStatus")

    val RecentlyCompletedJobs = table<String, MyJobs>("hpcRecentJobs")
}