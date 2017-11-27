package org.esciencecloud.abc.api

import org.apache.kafka.common.serialization.Serdes
import org.esciencecloud.abc.Request
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.kafka.StreamDescription
import org.esciencecloud.kafka.TableDescription

object HPCStreams {
    val AppRequests = StreamDescription<String, Request<HPCAppRequest>>("hpcAppRequests", Serdes.String(), jsonSerde())
    val AppEvents = StreamDescription<String, HPCAppEvent>("hpcAppEvents", Serdes.String(), jsonSerde())

    // TODO We can clean up in these
    val JobIdToApp = TableDescription<String, HPCAppEvent.Pending>("hpcJobToApp", Serdes.String(), jsonSerde())
    val SlurmIdToJobId = TableDescription<Long, String>("hpcSlurmIdToJobId", Serdes.Long(), Serdes.String())
    val JobIdToStatus = TableDescription<String, HPCAppEvent>("hpcJobToStatus", Serdes.String(), jsonSerde())
}