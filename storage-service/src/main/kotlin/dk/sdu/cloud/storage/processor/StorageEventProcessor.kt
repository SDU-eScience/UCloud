package dk.sdu.cloud.storage.processor

import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.batched
import dk.sdu.cloud.service.consumeBatchAndCommit
import java.util.Collections

typealias StorageEventHandler = (List<StorageEvent>) -> Unit

class StorageEventProcessor(
    private val eventConsumerFactory: EventConsumerFactory,
    private val parallelism: Int = 4
) {
    private val handlers: MutableList<StorageEventHandler> = Collections.synchronizedList(ArrayList())

    fun registerHandler(handler: StorageEventHandler) {
        handlers.add(handler)
    }

    fun removeHandler(handler: StorageEventHandler) {
        handlers.remove(handler)
    }

    fun init(): List<EventConsumer<*>> = (0 until parallelism).map { _ ->
        eventConsumerFactory.createConsumer(StorageEvents.events).configure { root ->
            root
                .batched(
                    batchTimeout = 500,
                    maxBatchSize = 1000
                )
                .consumeBatchAndCommit { batch ->
                    handlers.forEach { handler -> handler(batch.map { it.second }) }
                }
        }
    }
}
