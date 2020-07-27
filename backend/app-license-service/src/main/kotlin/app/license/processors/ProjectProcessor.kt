package dk.sdu.cloud.app.license.processors

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.app.license.services.AppLicenseService
import dk.sdu.cloud.service.Loggable

class ProjectProcessor(
    private val streams: EventStreamService,
    private val appLicenseService: AppLicenseService,
    private val description: ServiceDescription
) {
    fun init() {
        streams.subscribe(ProjectEvents.events, EventConsumer.Immediate(this::handleEvent), description.name)
    }

    private suspend fun handleEvent(event: ProjectEvent) {
        when (event) {
            is ProjectEvent.GroupDeleted -> {
                appLicenseService.deleteProjectGroupAclEntries(event.projectId, event.group)
            }

            else -> {
                log.debug("Discarding event: $event")
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
