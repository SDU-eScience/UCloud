package dk.sdu.cloud.file.favorite.processors

import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.indexing.api.subscriptionStream

class StorageEventProcessor(
    private val fileFavoriteService: FileFavoriteService<*>,
    private val eventStreamService: EventStreamService,
    private val name: String
) {
    fun init() {
        eventStreamService.subscribe(
            subscriptionStream(name), EventConsumer.Batched(
                maxLatency = 500L,
                maxBatchSize = 250
            ) { batch ->

                val eventBatch = batch.asSequence()
                    .filterIsInstance<StorageEvent.Deleted>()
                    .map { it.file.fileId }
                    .toSet()

                fileFavoriteService.deleteById(eventBatch)
            }
        )
    }
}
