package dk.sdu.cloud.activity.services

import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.FakeDBSessionFactory
import dk.sdu.cloud.service.paginate
import io.ktor.http.HttpStatusCode

interface ActivityEventDao<Session> {
    fun findByFileId(
        session: Session,
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityEvent>

    fun insertBatch(
        session: Session,
        events: List<ActivityEvent>
    ) {
        events.forEach { insert(session, it) }
    }

    fun insert(
        session: Session,
        event: ActivityEvent
    )
}

sealed class ActivityEventException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class NotFound : ActivityEventException("Not found", HttpStatusCode.NotFound)
}

class InMemoryActivityEventDao : ActivityEventDao<Unit> {
    private val database = HashMap<String, MutableList<ActivityEvent>>()
    private val lock = Any()

    override fun findByFileId(
        session: Unit,
        pagination: NormalizedPaginationRequest,
        fileId: String
    ): Page<ActivityEvent> {
        return database[fileId]?.paginate(pagination) ?: throw ActivityEventException.NotFound()
    }

    override fun insert(session: Unit, event: ActivityEvent) {
        synchronized(lock) {
            val current = database[event.fileId] ?: ArrayList()
            current.add(event)
            database[event.fileId] = current
        }
    }
}