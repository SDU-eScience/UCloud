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
                .filter { _, value -> value.event is HPCAppRequest.Start }
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

@JsonSerialize(using = MyJobs.Serializer::class)
@JsonDeserialize(using = MyJobs.Deserializer::class)
class MyJobs {
    companion object {
        const val RECENT_SIZE = 20
    }

    object Serializer : StdSerializer<MyJobs>(MyJobs::class.java) {
        private data class SerializedState(
                val startIdx: Int,
                val recentlyCompleted: Array<RunningJobStatus?>,
                val active: Map<String, RunningJobStatus>
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as SerializedState

                if (startIdx != other.startIdx) return false
                if (!Arrays.equals(recentlyCompleted, other.recentlyCompleted)) return false
                if (active != other.active) return false

                return true
            }

            override fun hashCode(): Int {
                var result = startIdx
                result = 31 * result + Arrays.hashCode(recentlyCompleted)
                result = 31 * result + active.hashCode()
                return result
            }
        }

        private fun JsonGenerator.writeRunningStatus(status: RunningJobStatus) {
            writeStartObject()
            writeStringField("id", status.jobId)
            writeStringField("status", status.status.name)
            writeEndObject()
        }

        override fun serialize(value: MyJobs, gen: JsonGenerator,
                               provider: SerializerProvider) {
            val (startIdx, recentlyCompleted, active) = synchronized(value.lock) {
                SerializedState(value.nextId, value.recentlyCompleted.copyOf(), HashMap(value.active))
            }

            // Value of -1 indicates start of loop. This simplifies the loop condition a bit
            var currentIdx = -1

            // Write in correct order (i.e. using nextId). This way we can deserialize correctly
            with(gen) {
                writeStartObject()

                writeArrayFieldStart("active")
                active.values.forEach { writeRunningStatus(it) }
                writeEndArray()

                writeArrayFieldStart("recent")
                while (currentIdx != startIdx - 1) {
                    if (currentIdx == -1) currentIdx = startIdx - 1

                    val status = recentlyCompleted[currentIdx]
                    currentIdx = (currentIdx + 1) % RECENT_SIZE // REMEMBER TO UPDATE THE INDEX

                    if (status == null) continue

                    writeRunningStatus(status)
                }
                writeEndArray()

                writeEndObject()
            }
        }
    }

    object Deserializer : StdDeserializer<MyJobs>(MyJobs::class.java) {
        private fun readRunningStatus(it: JsonNode) = RunningJobStatus(
                (it["id"] as TextNode).textValue(),
                JobStatus.valueOf((it["status"] as TextNode).textValue())
        )

        override fun deserialize(p: JsonParser, ctx: DeserializationContext): MyJobs {
            return MyJobs().apply {
                val node = p.codec.readTree<JsonNode>(p) as ObjectNode

                (node["recent"] as ArrayNode).forEach { handle(readRunningStatus(it)) }
                (node["active"] as ArrayNode).forEach { handle(readRunningStatus(it)) }
            }
        }
    }

    val active = HashMap<String, RunningJobStatus>()
    val recentlyCompleted = Array<RunningJobStatus?>(RECENT_SIZE) { null }
    private var nextId: Int = 0
    private var lock = Any()

    fun handle(event: RunningJobStatus) {
        when (event.status) {
            JobStatus.RUNNING, JobStatus.PENDING -> {
                synchronized(lock) {
                    active[event.jobId] = event
                }
            }

            JobStatus.COMPLETE, JobStatus.FAILURE -> {
                synchronized(lock) {
                    recentlyCompleted[nextId] = event
                    nextId = (nextId + 1) % RECENT_SIZE

                    active.remove(event.jobId)
                }
            }
        }
    }
}