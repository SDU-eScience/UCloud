package org.esciencecloud.abc.processors

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.esciencecloud.abc.services.HPCStreamService
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.abc.api.HPCStreams
import org.esciencecloud.abc.services.SlurmPollAgent
import org.esciencecloud.abc.util.aggregate
import org.esciencecloud.abc.util.filterIsInstance
import org.esciencecloud.kafka.JsonSerde.jsonSerde

class SlurmAggregate(
        private val streamServices: HPCStreamService,
        private val slurmPollAgent: SlurmPollAgent
) {
    fun init() {
        val pendingEvents = streamServices.appEvents.filterIsInstance(HPCAppEvent.Pending::class)

        // Slurm id to system id
        pendingEvents
                .map { systemId, event ->
                    KeyValue(event.jobId, systemId)
                }
                .groupByKey(Serdes.Long(), Serdes.String())
                .aggregate(HPCStreams.SlurmIdToJobId) { _, value, _ -> value }

        // System job id to app
        pendingEvents
                .groupByKey(Serdes.String(), jsonSerde())
                .aggregate(HPCStreams.JobIdToApp) { _, value, _ -> value }

        // Keep last status of every job. Also keeps state in Slurm poll agent
        streamServices.appEvents
                .groupByKey(Serdes.String(), jsonSerde())
                .aggregate(HPCStreams.JobIdToStatus) { _, value, _ ->
                    slurmPollAgent.handle(value)
                    value
                }
    }
}