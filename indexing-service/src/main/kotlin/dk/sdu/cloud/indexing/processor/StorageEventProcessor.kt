package dk.sdu.cloud.indexing.processor

import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.indexing.services.IndexingService
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.batched
import dk.sdu.cloud.service.consumeBatchAndCommit
import org.slf4j.Logger

class StorageEventProcessor(
    private val streamFactory: EventConsumerFactory,
    private val indexingService: IndexingService,
    private val parallelism: Int = 4
) {
    fun init(): List<EventConsumer<*>> {
        return (0 until parallelism).map { _ ->
            streamFactory.createConsumer(StorageEvents.events).configure { root ->
                root
                    .batched(
                        batchTimeout = 500,
                        maxBatchSize = 1000
                    )
                    .consumeBatchAndCommit { batch ->
                        indexingService.bulkHandleEvent(batch.map { it.second })
                    }
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
