package dk.sdu.cloud.service.test

import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.events.EventStreamState
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.runBlocking

data class MockedEvent<V : Any>(
    val topic: EventStream<V>,
    val value: V,
    val serializedValue: String
)

object EventServiceMock : EventStreamService, Loggable {
    override val log = logger()
    private val subscribers = HashMap<EventStream<*>, EventConsumer<*>>()
    val recordedEvents = ArrayList<MockedEvent<*>>()

    override fun <V : Any> createProducer(stream: EventStream<V>): EventProducer<V> {
        log.info("Creating producer for $stream")
        return EventProducerMock(stream)
    }

    override fun createStreams(streams: List<EventStream<*>>) {
        log.info("Creating streams: ${streams.map { it.name }}")
    }

    override fun describeStreams(names: List<String>): Map<String, EventStreamState?> {
        return names.map { it to null }.toMap()
    }

    override fun start() {
        log.info("Starting EventServiceMock")
    }

    override fun stop() {
        log.info("Stopping EventServiceMock")
    }

    override fun <V : Any> subscribe(stream: EventStream<V>, consumer: EventConsumer<V>) {
        subscribers[stream] = consumer
    }

    fun <V : Any> produceEvents(stream: EventStream<V>, events: List<V>) {
        recordedEvents.addAll(events.map { MockedEvent(stream, it, stream.serialize(it)) })

        @Suppress("UNCHECKED_CAST") val subscriber = subscribers[stream] as? EventConsumer<V> ?: return
        runBlocking {
            subscriber.accept(events)
        }
    }

    fun reset() {
        subscribers.clear()
        recordedEvents.clear()
    }

    fun <V : Any> messagesForTopic(topic: EventStream<V>): List<V> {
        @Suppress("UNCHECKED_CAST")
        return recordedEvents.asSequence()
            .filter { it.topic.name == topic.name }
            .map { it.value }
            .toList() as List<V>
    }

    fun <V : Any> rawMessagesForTopic(topic: EventStream<V>): List<String> {
        @Suppress("UNCHECKED_CAST")
        return recordedEvents.asSequence()
            .filter { it.topic.name == topic.name }
            .map { it.serializedValue }
            .toList()
    }
}

class EventProducerMock<V : Any>(override val stream: EventStream<V>) : EventProducer<V> {
    override suspend fun produce(events: List<V>) {
        log.info("Producing events: ${stream.name} -> $events")
        EventServiceMock.produceEvents(stream, events)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
