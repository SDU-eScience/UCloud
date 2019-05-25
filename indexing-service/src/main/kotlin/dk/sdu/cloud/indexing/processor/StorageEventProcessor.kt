package dk.sdu.cloud.indexing.processor

import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.indexing.services.IndexingService
import dk.sdu.cloud.service.Loggable
import org.slf4j.Logger

/**
 * Processes [StorageEvent]s
 *
 * @see IndexingService
 */
class StorageEventProcessor(
    private val streamService: EventStreamService,
    private val indexingService: IndexingService
) {
    fun init() {
        streamService.subscribe(
            StorageEvents.events,
            EventConsumer.Batched(
                maxLatency = BATCH_TIMEOUT_MS,
                maxBatchSize = MAX_ITEMS_IN_BATCH
            ) { batch ->
                log.debug(
                    "Handling another batch of ${batch.size} files. Head of batch: " +
                            "${batch.asSequence().take(DEBUG_ELEMENTS_IN_LOG).map { it }.toList()}..."
                )

                indexingService.bulkHandleEvent(batch)

                log.debug("Batch complete")
            }
        )
    }

    companion object : Loggable {
        override val log: Logger = logger()

        private const val BATCH_TIMEOUT_MS = 500L
        private const val MAX_ITEMS_IN_BATCH = 1000

        private const val DEBUG_ELEMENTS_IN_LOG = 5
    }
}
