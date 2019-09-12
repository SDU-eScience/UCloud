package dk.sdu.cloud.task.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.WithTimestamps
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.db.updateCriteria
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.task.api.Task
import io.ktor.http.HttpStatusCode
import org.hibernate.annotations.NaturalId
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "tasks")
internal class TaskEntity(
    @get:Id
    @get:NaturalId
    var jobId: String,

    var owner: String,

    var processor: String,

    var complete: Boolean,

    @get:Column(length = 1024 * 64)
    var title: String?,

    @get:Column(length = 1024 * 64)
    var statusMessage: String?,

    override var createdAt: Date = Date(),
    override var modifiedAt: Date = Date()
) : WithTimestamps {
    companion object : HibernateEntity<TaskEntity>, WithId<String>
}

internal class TaskHibernateDao : TaskDao<HibernateSession> {
    override fun findOrNull(session: HibernateSession, id: String, user: String): Task? {
        val entity = findInternalEntity(session, id, user)
        return toModel(entity)
    }

    private fun toModel(entity: TaskEntity): Task {
        return Task(
            jobId = entity.jobId,
            owner = entity.owner,
            processor = entity.processor,
            status = entity.statusMessage,
            title = entity.title,
            complete = entity.complete,
            startedAt = entity.createdAt.time
        )
    }

    private fun findInternalEntity(
        session: HibernateSession,
        id: String,
        user: String
    ): TaskEntity {
        val entity = session.criteria<TaskEntity> {
            entity[TaskEntity::jobId] equal id
        }.uniqueResult() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (entity.owner != user && entity.processor != user) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return entity
    }

    override fun updateStatus(session: HibernateSession, id: String, status: String, user: String): Boolean {
        return session.updateCriteria<TaskEntity>(
            setProperties = {
                criteria.set(entity[TaskEntity::statusMessage], status)
                criteria.set(entity[TaskEntity::modifiedAt], Date(System.currentTimeMillis()))
            },

            where = {
                (entity[TaskEntity::jobId] equal id) and (entity[TaskEntity::processor] equal user)
            }
        ).executeUpdate() > 0
    }

    override fun updateLastPing(session: HibernateSession, id: String, processor: SecurityPrincipal) {
        session.updateCriteria<TaskEntity>(
            setProperties = {
                criteria.set(entity[TaskEntity::modifiedAt], Date(System.currentTimeMillis()))
            },

            where = {
                (entity[TaskEntity::jobId] equal id) and (entity[TaskEntity::processor] equal processor.username)
            }
        ).executeUpdate()
    }

    override fun create(
        session: HibernateSession,
        title: String,
        initialStatus: String?,
        owner: String,
        processor: SecurityPrincipal
    ): String {
        if (processor.role !in Roles.PRIVILEDGED) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val id = UUID.randomUUID().toString()
        val entity = TaskEntity(
            id,
            owner,
            processor.username,
            false,
            title,
            initialStatus
        )
        session.save(entity)
        return id
    }

    override fun markAsComplete(session: HibernateSession, id: String, processor: SecurityPrincipal): Boolean {
        return session.updateCriteria<TaskEntity>(
            setProperties = {
                criteria.set(entity[TaskEntity::complete], true)
            },

            where = {
                (entity[TaskEntity::jobId] equal id) and (entity[TaskEntity::processor] equal processor.username)
            }
        ).executeUpdate() > 0
    }

    override fun list(
        session: HibernateSession,
        pagination: NormalizedPaginationRequest,
        user: SecurityPrincipal
    ): Page<Task> {
        return session.paginatedCriteria<TaskEntity>(pagination) {
            ((entity[TaskEntity::owner] equal user.username) or
                    (entity[TaskEntity::processor] equal user.username)) and
                    (entity[TaskEntity::complete] equal literal(true)) and
                    (entity[TaskEntity::modifiedAt] greaterThan
                            Date(System.currentTimeMillis() - (1000 * 60 * 60 * 15)))
        }.mapItems { toModel(it) }
    }
}
