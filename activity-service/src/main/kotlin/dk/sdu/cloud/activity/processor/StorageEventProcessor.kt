package dk.sdu.cloud.activity.processor

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

private typealias SEventTransformer = (StorageEvent) -> List<ActivityEvent>?

class StorageEventProcessor<DBSession>(
    private val streamFactory: EventConsumerFactory,
    private val db: DBSessionFactory<DBSession>,
    private val activityService: ActivityService<DBSession>,
    private val parallelism: Int = 4
) {
    private val transformers: List<SEventTransformer> = listOf(
        this::updatedTransformer
    )

    fun init(): List<EventConsumer<*>> = (0 until parallelism).map { _ ->
        streamFactory.createConsumer(StorageEvents.events).configure { root ->
            root
                .batched(
                    batchTimeout = 500,
                    maxBatchSize = 1000
                )
                .consumeBatchAndCommit { batch ->
                    if (batch.isEmpty()) return@consumeBatchAndCommit

                    log.debug("Consuming batch: $batch")

                    val events = batch.map { it.second }
                    val activityEvents = ArrayList<ActivityEvent>()
                    for (event in events) {
                        for (transformer in transformers) {
                            val transformedEvents = transformer(event)
                            if (transformedEvents != null) {
                                activityEvents.addAll(transformedEvents)
                            }
                        }
                    }

                    db.withTransaction { session ->
                        activityService.insertBatch(session, activityEvents)
                    }
                }
        }
    }

    private fun updatedTransformer(event: StorageEvent): List<ActivityEvent>? {
        if (event !is StorageEvent.CreatedOrRefreshed) return emptyList()
        val causedBy = event.eventCausedBy ?: return emptyList()

        return listOf(ActivityEvent.Updated(causedBy, event.timestamp, event.id))
    }

    companion object : Loggable {
        override val log = logger()
    }
}