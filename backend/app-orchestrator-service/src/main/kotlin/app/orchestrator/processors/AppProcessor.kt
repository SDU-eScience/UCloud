package app.orchestrator.processors

import dk.sdu.cloud.app.orchestrator.services.JobOrchestrator
import dk.sdu.cloud.app.store.api.AppEvent
import dk.sdu.cloud.app.store.api.AppStoreStreams
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.service.Loggable

class AppProcessor(
    private val streams: EventStreamService,
    private val jobService: JobOrchestrator<*>
) {
    fun init() {
        streams.subscribe(AppStoreStreams.AppDeletedStream, EventConsumer.Immediate(this::handleEvent))
    }

    private suspend fun handleEvent(event: AppEvent) {
        when(event) {
            is AppEvent.Deleted -> {
                log.info("Deleting job information: $event")
                jobService.deleteJobInformation(event.appName, event.appVersion)
            }
            else ->
                log.warn("Discarding event: $event")
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
