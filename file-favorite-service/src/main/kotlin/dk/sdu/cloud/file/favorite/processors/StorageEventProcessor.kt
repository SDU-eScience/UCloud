package dk.sdu.cloud.file.favorite.processors

import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.favorite.services.FileFavoriteService
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.batched
import dk.sdu.cloud.service.consumeBatchAndCommit

class StorageEventProcessor(
    private val fileFavoriteService: FileFavoriteService<*>,
    private val streamFactory: EventConsumerFactory,
    private val parallelism: Int = 4
) {
    fun init(): List<EventConsumer<*>> = (0 until parallelism).map {
        streamFactory.createConsumer(StorageEvents.events).configure { root ->
            root
                .batched(
                    batchTimeout = 500L,
                    maxBatchSize = 250
                )
                .consumeBatchAndCommit { batch ->
                    val eventBatch = batch.asSequence()
                        .filter { (_, event) -> event is StorageEvent.Deleted }
                        .map { it.second.id }
                        .toSet()

                    fileFavoriteService.deleteById(eventBatch)
                }
        }
    }
}
