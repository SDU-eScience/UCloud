package dk.sdu.cloud.task.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.task.api.Task
import dk.sdu.cloud.task.api.TaskUpdate
import io.ktor.http.HttpStatusCode

class TaskService(
    private val db: DBContext,
    private val dao: TaskAsyncDao,
    private val subscriptionService: SubscriptionService
) {
    suspend fun create(processor: SecurityPrincipal, title: String, status: String?, owner: String): Task {
        if (processor.role !in Roles.PRIVILEGED) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val startedAt = Time.now()
        val modifiedAt = Time.now()
        val jobId = dao.create(db, title, status, owner, processor)

        postStatus(processor, TaskUpdate(jobId, title, newStatus = status))
        return Task(jobId, owner, processor.username, title, null, false, startedAt, modifiedAt)
    }

    suspend fun postStatus(processor: SecurityPrincipal, status: TaskUpdate) {
        val jobId = status.jobId
        if (processor.role !in Roles.PRIVILEGED) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        dao.updateLastPing(db, jobId, processor)

        val task = dao.findOrNull(db, jobId, processor.username) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (task.processor != processor.username) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        subscriptionService.onTaskUpdate(task.owner, status)
    }

    suspend fun markAsComplete(processor: SecurityPrincipal, jobId: String) {
        if (processor.role !in Roles.PRIVILEGED) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        if (!dao.markAsComplete(db, jobId, processor)) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val task =  dao.findOrNull(db, jobId, processor.username)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        val update = TaskUpdate(jobId, complete = true)
        subscriptionService.onTaskUpdate(task.owner, update)
    }

    suspend fun list(user: SecurityPrincipal, pagination: NormalizedPaginationRequest): Page<Task> {
        return dao.list(db, pagination, user)
    }

    suspend fun find(user: SecurityPrincipal, jobId: String): Task {
        return dao.findOrNull(db, jobId, user.username) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }
}
