package dk.sdu.cloud.file.processors

import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.services.acl.AclService

class StorageProcessor(
    private val streams: EventStreamService,
    private val aclService: AclService<*>
) {
    fun init() {
        streams.subscribe(StorageEvents.events, EventConsumer.Batched { events ->
            val movedEvents = events.filterIsInstance<StorageEvent.Moved>()
            val deletedEvents = events.filterIsInstance<StorageEvent.Deleted>()
            val invalidatedEvents = events.filterIsInstance<StorageEvent.Invalidated>()

            aclService.handleFilesMoved(
                movedEvents.map { it.oldPath },
                movedEvents.map { it.file.path }
            )

            aclService.handleFilesDeleted(deletedEvents.map { it.file.path })
            aclService.handleFilesDeleted(invalidatedEvents.map { it.path })
        })
    }
}
