package dk.sdu.cloud.events

import dk.sdu.cloud.defaultMapper
import kotlinx.serialization.KSerializer

interface EventStream<V : Any> {
    val name: String
    val desiredPartitions: Int?
    val desiredReplicas: Short?
    val keySelector: (V) -> String

    fun serialize(event: V): String
    fun deserialize(value: String): V
}

data class JsonEventStream<V : Any>(
    override val name: String,
    val serializer: KSerializer<V>,
    override val keySelector: (V) -> String = { "" },
    override val desiredPartitions: Int? = null,
    override val desiredReplicas: Short? = null
) : EventStream<V> {
    override fun serialize(event: V): String {
        return defaultMapper.encodeToString(serializer, event)
    }

    override fun deserialize(value: String): V {
        return defaultMapper.decodeFromString(serializer, value)
    }
}

data class EventStreamState(
    val name: String,
    val partitions: Int?,
    val replicas: Short?
)

interface EventStreamService {
    fun <V : Any> subscribe(
        stream: EventStream<V>,
        consumer: EventConsumer<V>,
        rescheduleIdleJobsAfterMs: Long = 1000 * 60 * 5L
    )

    fun <V : Any> createProducer(stream: EventStream<V>): EventProducer<V>
    fun createStreams(streams: List<EventStream<*>>)
    fun describeStreams(names: List<String>): Map<String, EventStreamState?>

    fun start()
    fun stop()
}
