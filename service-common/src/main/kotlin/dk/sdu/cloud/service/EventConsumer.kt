package dk.sdu.cloud.service

import java.io.Closeable

interface ConsumedEvent<V> {
    val value: V

    fun <V2> map(newValue: V2): ConsumedEvent<V2>

    /**
     * Commits to the [EventConsumer] that this message, _and all before it_, have been consumed successfully.
     *
     * Note: The [EventConsumer] might have additional partitions, in order to improve parallelism, because of this
     * this method should be invoked on all events if [EventConsumer.commitConsumed] is not used. It is not possible to
     * skip some and then call [commit] on a single [ConsumedEvent].
     */
    fun commit()
}

interface EventConsumer<V> : Closeable {
    val isRunning: Boolean

    fun configure(configure: (processor: EventStreamProcessor<*, V>) -> Unit): EventConsumer<V>
    fun commitConsumed(events: List<ConsumedEvent<*>>)
    fun onExceptionCaught(handler: (Throwable) -> Unit)
}

interface EventConsumerFactory {
    fun <K, V> createConsumer(
        description: StreamDescription<K, V>,
        internalQueueSize: Int = 1024
    ): EventConsumer<Pair<K, V>>
}

interface EventStreamProcessor<ValueIn, ValueOut> {
    fun addChildProcessor(processor: EventStreamProcessor<ValueOut, *>)
    fun accept(events: List<ConsumedEvent<ValueIn>>)
    fun commitConsumed(events: List<ConsumedEvent<*>>)
}

abstract class AbstractEventStreamProcessor<ValueIn, ValueOut>(
    private val parent: EventStreamProcessor<*, ValueIn>
) : EventStreamProcessor<ValueIn, ValueOut> {
    private val children = ArrayList<EventStreamProcessor<ValueOut, *>>()

    internal abstract fun handleEvents(events: List<ConsumedEvent<ValueIn>>): List<ConsumedEvent<ValueOut>>

    override fun addChildProcessor(processor: EventStreamProcessor<ValueOut, *>) {
        children.add(processor)
    }

    override fun accept(events: List<ConsumedEvent<ValueIn>>) {
        val output = handleEvents(events)
        children.forEach { it.accept(output) }
    }

    override fun commitConsumed(events: List<ConsumedEvent<*>>) {
        parent.commitConsumed(events)
    }
}


