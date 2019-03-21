package dk.sdu.cloud.events

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef

abstract class EventStreamContainer {
    @PublishedApi
    internal val streams = ArrayList<EventStream<*>>()

    fun <V : Any> stream(stream: EventStream<V>): EventStream<V> {
        streams.add(stream)
        return stream
    }

    inline fun <reified V : Any> stream(
        name: String,
        noinline keySelector: (V) -> String,
        desiredPartitions: Int? = null,
        desiredReplicas: Short? = null
    ): EventStream<V> {
        val typeRef = jacksonTypeRef<V>()
        return stream(
            JsonEventStream(
                name,
                typeRef,
                keySelector,
                desiredPartitions,
                desiredReplicas
            ).also { streams.add(it) }
        )
    }
}
