package dk.sdu.cloud.task.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.bool
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.task.api.Task
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*

object TaskTable : SQLTable("tasks") {
    val jobId = text("job_id", notNull = true)
    val owner = text("owner", notNull = true)
    val processor = text("processor", notNull = true)
    val complete = bool("complete", notNull = true)
    val title = text("title")
    val statusMessage = text("status_message")
    val createdAt = timestamp("created_at", notNull = true)
    val modifiedAt = timestamp("modified_at", notNull = true)
}

class TaskAsyncDao {

    suspend fun findOrNull(db: DBContext, jobId: String, user: String): Task? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("jid", jobId)
                        setParameter("user", user)
                    },
                    """
                        SELECT * 
                        FROM tasks
                        WHERE job_id = :jid
                    """
                ).rows
                .firstOrNull()
        }?.toTask()
    }

    fun RowData.toTask(): Task {
        return Task(
            jobId = getField(TaskTable.jobId),
            owner = getField(TaskTable.owner),
            processor = getField(TaskTable.processor),
            status = getField(TaskTable.statusMessage),
            title = getField(TaskTable.title),
            complete = getField(TaskTable.complete),
            startedAt = getField(TaskTable.createdAt).toDate().time,
            modifiedAt = getField(TaskTable.modifiedAt).toDate().time
        )
    }

    private suspend fun findInternalEntity(
        db: DBContext,
        jobId: String,
        user: String
    ):Task  {
        val foundTask = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("jid", jobId)
                    },
                    """
                        SELECT * 
                        FROM tasks
                        WHERE job_id = :jid
                    """
                ).rows
                .singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }.toTask()
        if (foundTask.owner != user && foundTask.processor != user) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return foundTask
    }

    suspend fun updateStatus(db: DBContext, jobId: String, status: String, user: String): Boolean {
        val now = LocalDateTime(Time.now(), DateTimeZone.UTC).toDate().time
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("message", status)
                        setParameter("modified", now / 1000)
                        setParameter("jid", jobId)
                        setParameter("user", user)
                    },
                    """
                        UPDATE tasks
                        SET status_message = :message, modified_at = to_timestamp(:modified)
                        WHERE (job_id = :jid) AND (owner = :user)
                    """
                ).rowsAffected > 0L
        }
    }

    suspend fun updateLastPing(db: DBContext, jobId: String, processor: SecurityPrincipal) {
        db.withSession { session ->
            val now = LocalDateTime(Time.now(), DateTimeZone.UTC).toDate().time
            session
                .sendPreparedStatement(
                    {
                        setParameter("modified", now / 1000)
                        setParameter("jid", jobId)
                        setParameter("processor", processor.username)
                    },
                    """
                        UPDATE tasks
                        SET modified_at = to_timestamp(:modified)
                        WHERE (job_id = :jid) AND (processor = :processor) 
                    """
                )
        }
    }

    suspend fun create(
        db: DBContext,
        title: String,
        initialStatus: String?,
        owner: String,
        processor: SecurityPrincipal
    ): String {
        if (processor.role !in Roles.PRIVILEGED) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val jobId = UUID.randomUUID().toString()
        db.withSession { session ->
            val now = LocalDateTime(Time.now(), DateTimeZone.UTC)
            session.insert(TaskTable) {
                set(TaskTable.jobId, jobId)
                set(TaskTable.owner, owner)
                set(TaskTable.processor, processor.username)
                set(TaskTable.complete, false)
                set(TaskTable.title, title)
                set(TaskTable.statusMessage, initialStatus)
                set(TaskTable.createdAt, now)
                set(TaskTable.modifiedAt,now)
            }
        }
        return jobId
    }

    suspend fun markAsComplete(db: DBContext, jobId: String, processor: SecurityPrincipal): Boolean {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("complete", true)
                        setParameter("jid", jobId)
                        setParameter("processor", processor.username)
                    },
                    """
                        UPDATE tasks
                        SET complete = :complete
                        WHERE (job_id = :jid) AND (processor = :processor) 
                    """
                ).rowsAffected > 0
        }
    }

    suspend fun list(
        db: DBContext,
        pagination: NormalizedPaginationRequest,
        user: SecurityPrincipal
    ): Page<Task> {
        val timeLimit = LocalDateTime(Time.now(), DateTimeZone.UTC).toDate().time - (1000L * 60 * 60 * 15)
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("owner", user.username)
                        setParameter("complete", false)
                        setParameter("modified", timeLimit / 1000)
                    },
                    """
                        SELECT *
                        FROM tasks
                        WHERE (owner = :owner) AND 
                            (complete = :complete) AND 
                            (modified_at > to_timestamp(:modified))
                    """
                )
        }.rows.paginate(pagination)
            .mapItems { it.toTask() }
    }
}
