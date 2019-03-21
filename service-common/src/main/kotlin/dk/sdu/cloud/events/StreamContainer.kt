package dk.sdu.cloud.events

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef

abstract class StreamContainer {
    @PublishedApi
    internal val streams = ArrayList<EventStream<*>>()

    inline fun <reified V : Any> stream(
        name: String,
        noinline keySelector: (V) -> String,
        desiredPartitions: Int? = null,
        desiredReplicas: Short? = null
    ): EventStream<V> {
        val typeRef = jacksonTypeRef<V>()
        return EventStream(name, typeRef, keySelector, desiredPartitions, desiredReplicas).also { streams.add(it) }
    }
}
