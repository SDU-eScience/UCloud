package dk.sdu.cloud.events

interface EventProducer<V : Any> {
    val stream: EventStream<V>

    suspend fun produce(event: V) {
        produce(listOf(event))
    }

    suspend fun produce(events: List<V>)
}
