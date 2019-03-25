package dk.sdu.cloud.file.favorite.processors

import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.favorite.services.FileFavoriteService

class StorageEventProcessor(
    private val fileFavoriteService: FileFavoriteService<*>,
    private val eventStreamService: EventStreamService
) {
    fun init() {
        eventStreamService.subscribe(
            StorageEvents.events, EventConsumer.Batched(
                maxLatency = 500L,
                maxBatchSize = 250
            ) { batch ->

                val eventBatch = batch.asSequence()
                    .filter { event -> event is StorageEvent.Deleted }
                    .map { it.id }
                    .toSet()

                fileFavoriteService.deleteById(eventBatch)
            }
        )
    }
}
