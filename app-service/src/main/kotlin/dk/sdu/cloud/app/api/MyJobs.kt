package dk.sdu.cloud.app.api

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
import java.util.*

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
            writeStringField("systemId", status.jobId)
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
                (it["systemId"] as TextNode).textValue(),
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