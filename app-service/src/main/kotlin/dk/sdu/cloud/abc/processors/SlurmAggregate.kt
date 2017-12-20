package dk.sdu.cloud.abc.processors

import dk.sdu.cloud.abc.api.*
import dk.sdu.cloud.abc.services.*
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.Serialized
import org.esciencecloud.service.JsonSerde.jsonSerde
import org.esciencecloud.service.aggregate
import org.esciencecloud.service.filterIsInstance
import org.esciencecloud.service.toTable

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
