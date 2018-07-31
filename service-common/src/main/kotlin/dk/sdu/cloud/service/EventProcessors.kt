package dk.sdu.cloud.service

fun <ValueIn, ValueOut> EventStreamProcessor<*, ValueIn>.addProcessor(
    handler: EventStreamProcessor<ValueIn, ValueOut>.(events: List<ConsumedEvent<ValueIn>>) -> List<ConsumedEvent<ValueOut>>
): EventStreamProcessor<ValueIn, ValueOut> {
    return object : AbstractEventStreamProcessor<ValueIn, ValueOut>(this) {
        override fun handleEvents(events: List<ConsumedEvent<ValueIn>>): List<ConsumedEvent<ValueOut>> =
            handler(events)
    }.also { addChildProcessor(it) }
}

fun <ValueIn, ValueOut> EventStreamProcessor<*, ValueIn>.addValueProcessor(
    handler: EventStreamProcessor<ValueIn, ValueOut>.(events: ValueIn) -> ValueOut
): EventStreamProcessor<ValueIn, ValueOut> {
    return object : AbstractEventStreamProcessor<ValueIn, ValueOut>(this) {
        override fun handleEvents(events: List<ConsumedEvent<ValueIn>>): List<ConsumedEvent<ValueOut>> =
            events.map { it.map(handler(it.value)) }
    }.also { addChildProcessor(it) }
}

class BatchedEventStreamProcessor<V>(
    private val parent: EventStreamProcessor<*, V>,
    private val batchTimeout: Long,
    private val maxBatchSize: Int
) : EventStreamProcessor<V, V> {
    private val batch = ArrayList<ConsumedEvent<V>>()
    private var nextTimedEmit = System.currentTimeMillis() + batchTimeout
    private val children = ArrayList<EventStreamProcessor<V, *>>()

    init {
        parent.addChildProcessor(this)
    }

    override fun addChildProcessor(processor: EventStreamProcessor<V, *>) {
        children.add(processor)
    }

    override fun accept(events: List<ConsumedEvent<V>>) {
        for (item in events) {
            batch.add(item)

            if (batch.size >= maxBatchSize) emit()
        }

        if (batch.isNotEmpty() && System.currentTimeMillis() >= nextTimedEmit) emit()
    }

    private fun emit() {
        assert(batch.size <= maxBatchSize)
        val copyOfEvents = batch.toList()
        children.forEach { it.accept(copyOfEvents) }
        batch.clear()
        nextTimedEmit = System.currentTimeMillis() + batchTimeout
    }

    override fun commitConsumed(events: List<ConsumedEvent<*>>) {
        parent.commitConsumed(events)
    }
}

fun <V> EventStreamProcessor<*, V>.batched(batchTimeout: Long, maxBatchSize: Int): EventStreamProcessor<V, V> {
    return BatchedEventStreamProcessor(this, batchTimeout, maxBatchSize)
}

fun <V> EventStreamProcessor<*, V>.consumeBatch(handler: (List<ConsumedEvent<V>>) -> Unit) {
    addProcessor<V, Unit> {
        if (it.isNotEmpty()) {
            handler(it.map { it })
        }

        emptyList()
    }
}

fun <V> EventStreamProcessor<*, V>.consume(handler: (ConsumedEvent<V>) -> Unit) {
    addProcessor<V, Unit> {
        it.forEach { handler(it) }

        emptyList()
    }
}

fun <V> EventStreamProcessor<*, V>.consumeBatchAndCommit(handler: (List<V>) -> Unit) {
    addProcessor<V, Unit> {
        if (it.isNotEmpty()) {
            handler(it.map { it.value })
            commitConsumed(it)
        }

        emptyList()
    }
}

fun <V> EventStreamProcessor<*, V>.consumeAndCommit(handler: (V) -> Unit) {
    addProcessor<V, Unit> {
        it.forEach { handler(it.value) }
        commitConsumed(it)
        emptyList()
    }
}

data class DivergedOnPredicate<V>(val trueBranch: List<V>, val falseBranch: List<V>)

fun <V> List<V>.divergeOnPredicate(predicate: (V) -> Boolean): DivergedOnPredicate<V> {
    val satisfied = ArrayList<V>()
    val unsatisfied = ArrayList<V>()

    forEach {
        if (predicate(it)) satisfied.add(it)
        else unsatisfied.add(it)
    }

    return DivergedOnPredicate(satisfied, unsatisfied)
}

