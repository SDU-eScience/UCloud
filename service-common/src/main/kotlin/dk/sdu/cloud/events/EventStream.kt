package dk.sdu.cloud.events

import com.fasterxml.jackson.core.type.TypeReference
import dk.sdu.cloud.defaultMapper

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
    val typeReference: TypeReference<V>,
    override val keySelector: (V) -> String,
    override val desiredPartitions: Int? = null,
    override val desiredReplicas: Short? = null
) : EventStream<V> {
    private val reader = defaultMapper.readerFor(typeReference)
    private val writer = defaultMapper.writerFor(typeReference)

    override fun serialize(event: V): String {
        return writer.writeValueAsString(event)
    }

    override fun deserialize(value: String): V {
        return reader.readValue<V>(value)
    }
}

data class EventStreamState(
    val name: String,
    val partitions: Int?,
    val replicas: Short?
)

interface EventStreamService {
    fun <V : Any> subscribe(stream: EventStream<V>, consumer: EventConsumer<V>)
    fun <V : Any> createProducer(stream: EventStream<V>): EventProducer<V>
    fun createStreams(streams: List<EventStream<*>>)
    fun describeStreams(names: List<String>): Map<String, EventStreamState?>

    fun start()
    fun stop()
}
