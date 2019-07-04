package dk.sdu.cloud.activity.processor

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.service.Loggable

private typealias SEventTransformer = (StorageEvent) -> List<ActivityEvent>?

class StorageEventProcessor<DBSession>(
    private val streamFactory: EventStreamService,
    private val activityService: ActivityService<DBSession>
) {
    private val transformers: List<SEventTransformer> = listOf(
        this::updatedTransformer,
        this::deletedTransformer
    )

    fun init() {
        streamFactory.subscribe(StorageEvents.events, EventConsumer.Batched(
            maxLatency = 500L,
            maxBatchSize = 1000
        ) { events ->
            if (events.isEmpty()) return@Batched

            log.debug("Consuming batch: $events")

            val activityEvents = ArrayList<ActivityEvent>()
            for (event in events) {
                for (transformer in transformers) {
                    val transformedEvents = transformer(event)
                    if (transformedEvents != null) {
                        activityEvents.addAll(transformedEvents)
                    }
                }
            }

            activityService.insertBatch(activityEvents)
        })
    }

    private fun updatedTransformer(event: StorageEvent): List<ActivityEvent>? {
        if (event !is StorageEvent.CreatedOrRefreshed) return emptyList()
        val causedBy = event.eventCausedBy ?: return emptyList()

        return listOf(
            ActivityEvent.Updated(
                username = causedBy,
                timestamp = event.timestamp,
                fileId = event.file.fileId,
                originalFilePath = event.file.path
            )
        )
    }

    private fun deletedTransformer(event: StorageEvent): List<ActivityEvent>? {
        if (event !is StorageEvent.Deleted) return emptyList()
        val causedBy = event.eventCausedBy ?: return emptyList()

        return listOf(
            ActivityEvent.Deleted(
                timestamp = event.timestamp,
                fileId = event.file.fileId,
                username = causedBy,
                originalFilePath = event.file.path
            )
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
