package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.file.api.*

interface Migratable {
    fun migrate() {}
}

data class BulkIndexingResponse(
    val failures: List<String>
)

interface IndexingService : Migratable {
    fun handleEvent(event: StorageEvent)

    fun bulkHandleEvent(events: List<StorageEvent>): BulkIndexingResponse {
        events.forEach { handleEvent(it) }
        return BulkIndexingResponse(emptyList())
    }
}
