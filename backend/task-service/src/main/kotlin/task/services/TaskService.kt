package dk.sdu.cloud.task.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.util.ProviderCommunicationsV2
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.task.api.*

class TaskService(
    private val db: DBContext,
    private val subscriptionService: SubscriptionService,
    private val providerComm: ProviderCommunicationsV2
) {
    suspend fun create(actorAndProject: ActorAndProject, request: CreateRequest): BackgroundTask {
        val initTime = Time.now()
        val jobId = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("current_time", initTime)
                    setParameter("username", request.user)
                    setParameter("provider", request.provider)
                    setParameter("operation", request.operation)
                    setParameter("progress", request.progress)
                    setParameter("cancel", request.canCancel)
                    setParameter("pause", request.canPause)
                },
                """
                    insert into task.tasks_v2 
                    (created_at, modified_at, created_by, owned_by, operation, progress, can_cancel, can_pause) 
                    values 
                    (to_timestamp(:current_time/1000), to_timestamp(:current_time), :username, :provider, :operation, :progress, :cancel, :pause)
                    returning id
                """
            ).rows.singleOrNull()?.getLong(0) ?: throw RPCException("Failed to create task", HttpStatusCode.UnprocessableEntity)
        }

        return BackgroundTask(
            taskId = jobId,
            createdAt = initTime,
            modifiedAt = initTime,
            createdBy = request.user,
            provider = request.provider,
            status = BackgroundTask.Status(
                operation = request.operation ?: DEFAULT_TASK_OPERATION,
                progress = request.progress ?: DEFAULT_TASK_PROGRESS
            ),
            specification = BackgroundTask.Specification(
                request.canPause,
                request.canCancel
            ))
    }

    private suspend fun updateBackgroundTask(actorAndProject: ActorAndProject, update: BackgroundTaskUpdate) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("task_id", update.taskId)
                    setParameter("modify", update.modifiedAt)
                    setParameter("state", update.newStatus.state.toString())
                    setParameter("op", update.newStatus.operation)
                    setParameter("progress", update.newStatus.progress)
                },
                """
                    update task.tasks_v2
                    set modified_at = to_timestamp(:modify/1000), state = :state, operation = :op, progress = :progress
                    where id = :task_id
                """
            )
        }
    }

    private suspend fun forwardUserAction(actorAndProject: ActorAndProject, provider: String, update: BackgroundTaskUpdate) {
        providerComm.call(
            provider,
            actorAndProject,
            {
                TasksProvider(it).userAction
            },
            PostStatusRequest(update),
            isUserRequest = true
        )
    }

    suspend fun userAction(actorAndProject: ActorAndProject, update: BackgroundTaskUpdate) {
        if (actorAndProject.actor !is Actor.User) throw RPCException("This is not a system call - use postUpdate", HttpStatusCode.BadRequest)

        val taskId = update.taskId
        val foundTask = findById(taskId)
        if (foundTask.createdBy != actorAndProject.actor.safeUsername()) throw RPCException("Task not found", HttpStatusCode.NotFound)
        //Note(HENRIK) If task is already finished all good? notify user?
        if (foundTask.status.state == TaskState.FAILURE || foundTask.status.state == TaskState.SUCCESS) return

        val newState = update.newStatus.state
        if (newState == TaskState.SUSPENDED && !foundTask.specification.canPause) {
            throw RPCException("Task cannot be paused", HttpStatusCode.BadRequest)
        }
        if (newState == TaskState.CANCELLED && !foundTask.specification.canCancel) {
            throw RPCException("Task cannot be cancelled", HttpStatusCode.BadRequest)
        }

        val normalizedUpdate = update.copy(
            newStatus = BackgroundTask.Status(
                update.newStatus.state,
                when (newState) {
                    TaskState.CANCELLED -> "Operation cancelled by user"
                    TaskState.SUSPENDED -> "Operation paused by user"
                    else -> throw RPCException("Unknown state", HttpStatusCode.BadRequest)
                },
                when (newState) {
                    TaskState.CANCELLED -> "Cancelled"
                    TaskState.SUSPENDED -> {
                        if(foundTask.status.state == TaskState.RUNNING) {
                            foundTask.status.progress
                        } else {""}
                    }
                    else -> throw RPCException("Unknown state", HttpStatusCode.BadRequest)
                }
            )
        )

        updateBackgroundTask(actorAndProject, normalizedUpdate)

        forwardUserAction(actorAndProject, foundTask.provider, normalizedUpdate)

        val taskFound = findById(taskId)
        subscriptionService.onTaskUpdate(taskFound.createdBy, taskFound)
    }

    suspend fun postStatus(actorAndProject: ActorAndProject, update: BackgroundTaskUpdate) {
        val taskId = update.taskId

        updateBackgroundTask(actorAndProject, update)

        val taskFound = findById(taskId)

        subscriptionService.onTaskUpdate(taskFound.createdBy, taskFound)
    }

    suspend fun markAsComplete(actorAndProject: ActorAndProject, jobId: Long) {
        val success = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("task_id", jobId)
                },
                """
                   update task.tasks_v2
                   set state = 'SUCCESS', progress = 'Done', modified_at = now(), can_pause = false, can_cancel = false
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
                    select id, extract(epoch from created_at)::bigint, extract(epoch from modified_at)::bigint, created_by, state, operation, progress, can_pause, can_cancel 
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

    private suspend fun findById(jobId: Long): BackgroundTask {
        return db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("task_id", jobId)
                },
                """
                    select id, extract(epoch from created_at)::bigint, extract(epoch from modified_at)::bigint, created_by, owned_by, state, operation, progress, can_pause, can_cancel 
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
        provider = getString(4)!!,
        status = BackgroundTask.Status(
            TaskState.valueOf(getString(5)!!),
            getString(6)!!,
            getString(7)!!
        ),
        specification = BackgroundTask.Specification(
            getBoolean(8)!!,
            getBoolean(9)!!
        )
    )

}
