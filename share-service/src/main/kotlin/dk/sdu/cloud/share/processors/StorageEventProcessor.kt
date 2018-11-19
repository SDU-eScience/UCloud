package dk.sdu.cloud.share.processors

import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.batched
import dk.sdu.cloud.service.consumeBatchAndCommit
import dk.sdu.cloud.share.services.ShareService
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class StorageEventProcessor(
    private val shareService: ShareService<*>,
    private val streamFactory: EventConsumerFactory,
    private val parallelism: Int = 4
) {
    fun init(): List<EventConsumer<*>> = (0 until parallelism).map {
        streamFactory.createConsumer(StorageEvents.events).configure { root ->
            root
                .batched(
                    batchTimeout = 500L,
                    maxBatchSize = 100
                )
                .consumeBatchAndCommit { batch ->
                    // TODO I am concerned about performance.
                    // We will have to look at all file events in the entire system in order to check if the shared
                    // files have been changed. This seems very problematic at scale.

                    val invalidatedAndDeletes = ArrayList<StorageEvent>()
                    val moved = ArrayList<StorageEvent.Moved>()

                    for ((_, event) in batch) {
                        if (event is StorageEvent.Invalidated || event is StorageEvent.Deleted) {
                            invalidatedAndDeletes.add(event)
                        } else if (event is StorageEvent.Moved) {
                            moved.add(event)
                        }
                    }

                    GlobalScope.launch {
                        val movedJob = launch { shareService.handleFilesMoved(moved) }
                        val deletedJob = launch { shareService.handleFilesDeletedOrInvalidated(invalidatedAndDeletes) }
                        movedJob.join()
                        deletedJob.join()
                    }
                }
        }
    }
}
