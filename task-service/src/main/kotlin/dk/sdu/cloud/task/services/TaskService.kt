package dk.sdu.cloud.task.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.task.api.Task
import dk.sdu.cloud.task.api.TaskUpdate
import io.ktor.http.HttpStatusCode

class TaskService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: TaskDao<Session>,
    private val subscriptionService: SubscriptionService<Session>
) {
    fun create(processor: SecurityPrincipal, title: String, status: String?, owner: String): Task {
        if (processor.role !in Roles.PRIVILEDGED) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val startedAt = System.currentTimeMillis()
        val id = db.withTransaction { session ->
            dao.create(session, title, status, owner, processor)
        }

        return Task(id, owner, processor.username, title, null, false, startedAt)
    }

    suspend fun postStatus(processor: SecurityPrincipal, id: String, status: TaskUpdate) {
        if (processor.role !in Roles.PRIVILEDGED) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val isFromOwnService = processor.role == Role.SERVICE && processor.username == "_task"
        val task =
            db.withTransaction { dao.findOrNull(it, id, processor.username) }
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (!isFromOwnService) {
            if (task.processor != processor.username) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }

        subscriptionService.onTaskUpdate(task.owner, id, status, allowRemoteCalls = !isFromOwnService)
    }

    suspend fun markAsComplete(processor: SecurityPrincipal, id: String) {
        if (processor.role !in Roles.PRIVILEDGED) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        val task = db.withTransaction { session ->
            if (!dao.markAsComplete(session, id, processor)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            dao.findOrNull(session, id, processor.username)
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }

        val update = TaskUpdate(complete = true)
        subscriptionService.onTaskUpdate(task.owner, id, update)
    }

    fun list(user: SecurityPrincipal, pagination: NormalizedPaginationRequest): Page<Task> {
        return db.withTransaction { session ->
            dao.list(session, pagination, user)
        }
    }

    fun find(user: SecurityPrincipal, id: String): Task {
        return db.withTransaction { session ->
            dao.findOrNull(session, id, user.username)
        } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }
}
