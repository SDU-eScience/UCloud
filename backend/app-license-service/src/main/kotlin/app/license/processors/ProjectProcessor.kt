package dk.sdu.cloud.app.license.processors

import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.app.license.services.AppLicenseService
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.HibernateSession

class ProjectProcessor(
    private val streams: EventStreamService,
    private val appLicenseService: AppLicenseService<HibernateSession>
) {
    fun init() {
        streams.subscribe(ProjectEvents.events, EventConsumer.Immediate(this::handleEvent))
    }

    private suspend fun handleEvent(event: ProjectEvent) {
        when (event) {
            is ProjectEvent.GroupDeleted -> {
                appLicenseService.deleteProjectGroupAclEntries(event.project.id, event.group)

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
