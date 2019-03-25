package dk.sdu.cloud.project.auth.processors

import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.project.auth.api.ProjectAuthEvents
import dk.sdu.cloud.project.auth.services.AuthTokenDao
import dk.sdu.cloud.project.auth.services.ProjectInitializedListener
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class ProjectAuthEventProcessor<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val authTokenDao: AuthTokenDao<DBSession>,
    private val streams: EventStreamService
) {
    private val listeners = ArrayList<ProjectInitializedListener>()

    fun addListener(listener: ProjectInitializedListener) {
        listeners.add(listener)
    }

    fun init() {
        streams.subscribe(ProjectAuthEvents.events, EventConsumer.Immediate { event ->
            val tokens = db.withTransaction { session ->
                authTokenDao.tokensForProject(session, event.projectId)
            }

            listeners.forEach { it.onProjectCreated(event.projectId, tokens) }
        })
    }
}
