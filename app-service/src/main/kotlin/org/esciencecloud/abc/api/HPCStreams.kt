package org.esciencecloud.abc.api

import org.esciencecloud.abc.processors.MyJobs
import org.esciencecloud.service.KafkaDescriptions
import org.esciencecloud.service.KafkaRequest

object HPCStreams : KafkaDescriptions() {
    /*
    val AppRequests = HPCApplications.AppRequest.descriptions.mappedAtGateway("request.hpcApp") {
        Pair(it.header.uuid, it)
    }
    */
    val AppRequests = stream<String, KafkaRequest<HPCAppRequest>>("request.hpcApp")
    val AppEvents = stream<String, HPCAppEvent>("hpcAppEvents")

    // TODO We can clean up in these
    val JobIdToApp = table<String, HPCAppEvent.Pending>("hpcJobToApp")
    val SlurmIdToJobId = table<Long, String>("hpcSlurmIdToJobId")
    val JobIdToStatus = table<String, HPCAppEvent>("hpcJobToStatus")

    val RecentlyCompletedJobs = table<String, MyJobs>("hpcRecentJobs")
}