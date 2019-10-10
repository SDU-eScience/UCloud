package dk.sdu.cloud.share.processors

import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.indexing.api.subscriptionStream
import dk.sdu.cloud.share.services.ProcessingService
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class StorageEventProcessor(
    private val processingService: ProcessingService<*>,
    private val streamFactory: EventStreamService,
    private val serviceName: String
) {
    fun init() {
        streamFactory.subscribe(
            subscriptionStream(serviceName),
            EventConsumer.Batched(maxBatchSize = 100, maxLatency = 500L) { batch ->
                val deletes = ArrayList<StorageEvent.Deleted>()
                val moved = ArrayList<StorageEvent.Moved>()

                for (event in batch) {
                    // TODO Handle invalidated
                    if (event is StorageEvent.Deleted) {
                        deletes.add(event)
                    } else if (event is StorageEvent.Moved) {
                        moved.add(event)
                    }
                }

                coroutineScope {
                    val movedJob = launch { processingService.handleFilesMoved(moved) }
                    val deletedJob = launch { processingService.handleFilesDeletedOrInvalidated(deletes) }
                    movedJob.join()
                    deletedJob.join()
                }
            }
        )
    }


}
