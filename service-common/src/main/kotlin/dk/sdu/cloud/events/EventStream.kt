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

inline fun <reified V : Any> EventStream(
    name: String,
    noinline keySelector: (V) -> String,
    desiredPartitions: Int? = null,
    desiredReplicas: Short? = null
): EventStream<V> {
    val typeRef = jacksonTypeRef<V>()
    return EventStream(name, typeRef, keySelector, desiredPartitions, desiredReplicas)
}

interface EventStreamService {
    fun <V : Any> subscribe(stream: EventStream<V>, consumer: EventConsumer<V>)
    fun <V : Any> createProducer(stream: EventStream<V>): EventProducer<V>

    suspend fun start()
    suspend fun stop()
}
