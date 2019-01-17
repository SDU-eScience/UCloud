package dk.sdu.cloud.project.auth.processors

import dk.sdu.cloud.project.auth.api.ProjectAuthEvents
import dk.sdu.cloud.project.auth.services.AuthTokenDao
import dk.sdu.cloud.project.auth.services.ProjectInitializedListener
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.consumeAndCommit
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.runBlocking

class ProjectAuthEventProcessor<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val authTokenDao: AuthTokenDao<DBSession>,
    private val eventConsumerFactory: EventConsumerFactory,
    private val parallelism: Int = 4
) {
    private val listeners = ArrayList<ProjectInitializedListener>()

    fun addListener(listener: ProjectInitializedListener) {
        listeners.add(listener)
    }

    fun init(): List<EventConsumer<*>> = (0 until parallelism).map { _ ->
        eventConsumerFactory.createConsumer(ProjectAuthEvents.events).configure { root ->
            root.consumeAndCommit { (_, event) ->
                val tokens = db.withTransaction { session ->
                    authTokenDao.tokensForProject(session, event.projectId)
                }

                runBlocking {
                    listeners.forEach { it.onProjectCreated(event.projectId, tokens) }
                }
            }
        }
    }
}
