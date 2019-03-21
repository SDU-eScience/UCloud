package dk.sdu.cloud.events

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef

data class EventStream<V : Any>(
    val name: String,
    val typeReference: TypeReference<V>,
    val keySelector: (V) -> String,
    val desiredPartitions: Int? = null,
    val desiredReplicas: Short? = null
)

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

    suspend fun start()
    suspend fun stop()
}
