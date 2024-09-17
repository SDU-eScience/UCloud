package dk.sdu.cloud.task.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.task.api.*

class TaskService(
    private val db: DBContext,
    private val subscriptionService: SubscriptionService
) {
    suspend fun create(actorAndProject: ActorAndProject, request: CreateRequest): BackgroundTask {
        if (actorAndProject.actor is Actor.User ) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val initTime = Time.now()

        val jobId = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("current_time", initTime)
                    setParameter("username", request.user)
                    setParameter("provider", request.provider)
                    setParameter("operation", request.operation)
                    setParameter("process", request.progress)
                    setParameter("cancel", request.canCancel)
                    setParameter("pause", request.canPause)
                },
                """
                    insert into task.tasks_v2 
                    (created_at, modified_at, created_by, owned_by, operation, progress, can_cancel, can_pause) 
                    values 
                    (:current_time, :current_time, :username, :provider, :operation, :progress, :cancel, :pause)
                    returning id
                """
            ).rows.singleOrNull()?.getLong(0) ?: throw RPCException("Failed to create task", HttpStatusCode.UnprocessableEntity)
        }

        return BackgroundTask(
            taskId = jobId,
            createdAt = initTime,
            modifiedAt = initTime,
            createdBy = request.user,
            status = BackgroundTask.Status(
                operation = request.operation ?: DEFAULT_TASK_OPERATION,
                progress = request.progress ?: DEFAULT_TASK_PROGRESS
            ),
            specification = BackgroundTask.Specification(
                request.canPause,
                request.canCancel
            ))
    }

    suspend fun postStatus(actorAndProject: ActorAndProject, update: BackgroundTaskUpdate) {
        val taskId = update.taskId
        if (actorAndProject.actor is Actor.User ) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("task_id", taskId)
                    setParameter("modify", update.modifiedAt)
                    setParameter("state", update.newStatus.state.toString())
                    setParameter("op", update.newStatus.operation)
                    setParameter("progress", update.newStatus.progress)
                },
                """
                    update task.tasks_v2
                    set modified_at = :modify, state = :state, operation = :op, progress = :progress
                    where id = :task_id
                """
            )
        }

        val taskFound = findById(taskId)

        if (taskFound.createdBy != actorAndProject.actor.safeUsername()) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        subscriptionService.onTaskUpdate(taskFound.createdBy, taskFound)
    }

    suspend fun markAsComplete(actorAndProject: ActorAndProject, jobId: Long) {
        if (actorAndProject.actor is Actor.User) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val success = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("task_id", jobId)
                },
                """
                   update task.tasks_v2
                   set state = 'SUCCESS', modified_at = now(), can_pause = false, can_cancel = false
                   where id = :task_id
                """
            ).rowsAffected > 0
        }
        if (!success) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val task = findById(jobId)
        subscriptionService.onTaskUpdate(actorAndProject.actor.safeUsername(), task)
    }

    suspend fun list(actorAndProject: ActorAndProject, request: ListRequest): PageV2<BackgroundTask> {
        return db.paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                    },
                    """
                    select id, created_at, modified_at, created_by, state, operation, progress, can_pause, can_cancel 
                    from task.tasks_v2 
                    where created_by = :username
                    order by created_at desc
                """
                )
            },
            mapper = { session, rows -> rows.map { it.toBackgroundTask() }}
        )
    }

    suspend fun find(actorAndProject: ActorAndProject, id: FindByStringId): BackgroundTask {
        val task = findById(id.id.toLong())
        if (task.createdBy == actorAndProject.actor.safeUsername() || actorAndProject.actor == Actor.System) {
            return task
        } else {
            throw RPCException("Task not found", HttpStatusCode.NotFound)
        }
    }

    private suspend fun findByUser(username: String): List<BackgroundTask> {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", username)
                },
                """
                    select id, created_at, modified_at, created_by, state, operation, progress, can_pause, can_cancel 
                    from task.tasks_v2 
                    where created_by = :username
                    order by created_at desc
                """
            ).rows.map {
                it.toBackgroundTask()
            }
        }
    }

    private suspend fun findById(jobId: Long): BackgroundTask {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("task_id", jobId)
                },
                """
                    select id, created_at, modified_at, created_by, state, operation, progress, can_pause, can_cancel 
                    from task.tasks_v2 
                    where id = :task_id
                """
            ).rows.map {
                it.toBackgroundTask()
            }.singleOrNull() ?: throw RPCException("Failed to find task", HttpStatusCode.NotFound)
        }
    }

    private fun RowData.toBackgroundTask() = BackgroundTask(
        taskId = getLong(0)!!,
        createdAt = getLong(1)!!,
        modifiedAt = getLong(2)!!,
        createdBy = getString(3)!!,
        status = BackgroundTask.Status(
            TaskState.valueOf(getString(4)!!),
            getString(5)!!,
            getString(6)!!
        ),
        specification = BackgroundTask.Specification(
            getBoolean(7)!!,
            getBoolean(8)!!
        )
    )

}
