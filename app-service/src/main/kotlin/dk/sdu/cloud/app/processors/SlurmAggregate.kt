package dk.sdu.cloud.app.processors

import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.*
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.Serialized
import dk.sdu.cloud.service.JsonSerde.jsonSerde
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.aggregate
import dk.sdu.cloud.service.filterIsInstance
import dk.sdu.cloud.service.toTable

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

        val ownerByJobId = streamServices.rawAppRequests
                .filter { _, value -> value.event is AppRequest.Start }
                .mapValues { TokenValidation.validateOrNull(it.header.performedFor)?.subject }
                .filter { _, value -> value != null }
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
