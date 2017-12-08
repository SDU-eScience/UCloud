package org.esciencecloud.abc.processors

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.Serialized
import org.esciencecloud.abc.api.*
import org.esciencecloud.abc.api.HPCApplicationDescriptions.AppRequest
import org.esciencecloud.abc.services.HPCStreamService
import org.esciencecloud.abc.services.SlurmPollAgent
import org.esciencecloud.service.JsonSerde.jsonSerde
import org.esciencecloud.service.aggregate
import org.esciencecloud.service.filterIsInstance
import org.esciencecloud.service.toTable
import java.util.*

class SlurmAggregate(
        private val streamServices: HPCStreamService,
        private val slurmPollAgent: SlurmPollAgent
) {
    fun init() {
        // TODO Doing queries against the state store seems more expensive that it needs to be. We should use joins!
        val pendingEvents = streamServices.appEvents.filterIsInstance(HPCAppEvent.Pending::class)

        // Slurm id to system id
        pendingEvents
                .map { systemId, event ->
                    KeyValue(event.jobId, systemId)
                }
                .groupByKey(Serialized.with(Serdes.Long(), Serdes.String()))
                .aggregate(HPCStreams.SlurmIdToJobId) { _, value, _ -> value }

        // System job id to app
        pendingEvents
                .groupByKey(Serialized.with(Serdes.String(), jsonSerde()))
                .aggregate(HPCStreams.JobIdToApp) { _, value, _ -> value }

        // Keep last status of every job. Also keeps state in Slurm poll agent
        streamServices.appEvents
                .groupByKey(Serialized.with(Serdes.String(), jsonSerde()))
                .aggregate(HPCStreams.JobIdToStatus) { _, value, _ ->
                    slurmPollAgent.handle(value)
                    value
                }

        // These are not authenticated, but this doesn't matter when we join them with the event stream.
        // This means we only use the requests that we acted upon regardless (i.e. authenticated requests).
        val ownerByJobId = streamServices.rawAppRequests
                .filter { _, value -> value.event is AppRequest.Start }
                .mapValues { it.header.performedFor.username }
                .toTable(Serdes.String(), Serdes.String())

        val runningJobsByOwner = streamServices.appEvents.join(ownerByJobId) { event, owner ->
            Pair(owner, event.toJobStatus())
        }.map { jobId, (owner, status) ->
            KeyValue(owner, RunningJobStatus(jobId, status))
        }.groupByKey(Serialized.with(Serdes.String(), jsonSerde()))

        runningJobsByOwner.aggregate(
                target = HPCStreams.RecentlyCompletedJobs,
                initializer = { MyJobs() },
                aggregate = { _, status, agg ->
                    agg!!.also { it.handle(status) }
                }
        )
    }
}
