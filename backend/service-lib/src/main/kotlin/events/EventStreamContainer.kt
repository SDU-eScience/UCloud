package dk.sdu.cloud.events

import kotlinx.serialization.KSerializer

abstract class EventStreamContainer {
    @PublishedApi
    internal val streams = ArrayList<EventStream<*>>()

    fun <V : Any> stream(stream: EventStream<V>): EventStream<V> {
        streams.add(stream)
        return stream
    }

    fun <V : Any> stream(
        serializer: KSerializer<V>,
        name: String,
        keySelector: (V) -> String,
        desiredPartitions: Int? = null,
        desiredReplicas: Short? = null
    ): EventStream<V> {
        return stream(
            JsonEventStream(
                name,
                serializer,
                keySelector,
                desiredPartitions,
                desiredReplicas
            ).also { streams.add(it) }
        )
    }
}
