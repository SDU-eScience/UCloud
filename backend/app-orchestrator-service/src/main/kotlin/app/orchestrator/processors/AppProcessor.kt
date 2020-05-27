package dk.sdu.cloud.app.orchestrator.processors

import dk.sdu.cloud.app.orchestrator.services.ApplicationService
import dk.sdu.cloud.app.orchestrator.services.JobOrchestrator
import dk.sdu.cloud.app.store.api.AppEvent
import dk.sdu.cloud.app.store.api.AppStoreStreams
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.service.Loggable

class AppProcessor(
    private val streams: EventStreamService,
    private val jobService: JobOrchestrator,
    private val appService: ApplicationService
) {
    fun init() {
        streams.subscribe(AppStoreStreams.AppDeletedStream, EventConsumer.Immediate(this::handleEvent))
    }

    private suspend fun handleEvent(event: AppEvent) {
        when(event) {
            is AppEvent.Deleted -> {
                log.info("Deleting job information: $event")
                // When app is deleted old information would have to be deleted
                jobService.deleteJobInformation(event.appName, event.appVersion)
                // appStoreService has a cache that removes the need to contact appStore.
                // Resetting the cache would force us to see if an app is still there.
                appService.apps.clearAll()
            }
            else ->
                log.warn("Discarding event: $event")
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
