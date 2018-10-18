package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.StorageEvent

/**
 * An interface which marks that the underlying class supports migration
 *
 * This interface is not yet stable. When stable it should be promoted to service-common
 */
interface Migratable {
    fun migrate() {}
}

/**
 * A response to indexing events in bulk
 *
 * @see IndexingService.bulkHandleEvent
 */
data class BulkIndexingResponse(
    val failures: List<String>
)

/**
 * A service for indexing files based on [StorageEvent]s.
 *
 * This interface only deals with the insertion for queries see [IndexQueryService]
 */
interface IndexingService : Migratable {
    /**
     * Handles indexing actions based on a single [StorageEvent]
     *
     * For bulk inserts use [bulkHandleEvent]
     */
    fun handleEvent(event: StorageEvent)

    /**
     * Handles indexing actions based on multiple events.
     *
     * This method is likely more efficient than [handleEvent] if multiple events are used.
     */
    fun bulkHandleEvent(events: List<StorageEvent>): BulkIndexingResponse {
        events.forEach { handleEvent(it) }
        return BulkIndexingResponse(emptyList())
    }
}
