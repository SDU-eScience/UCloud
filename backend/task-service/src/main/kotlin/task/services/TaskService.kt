package dk.sdu.cloud.task.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.util.ProviderCommunicationsV2
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
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
        val (actor) = actorAndProject
        val providerId = actor.safeUsername()
            .takeIf { it.startsWith(AuthProviders.PROVIDER_PREFIX) }
            ?.removePrefix(AuthProviders.PROVIDER_PREFIX)
            ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val initTime = Time.now()

        val jobId = db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", request.user)
                    setParameter("provider", providerId)
                    setParameter("title", request.title)
                    setParameter("body", request.body)
                    setParameter("progress", request.progress)
                    setParameter("cancel", request.canCancel)
                    setParameter("pause", request.canPause)
                    setParameter("icon", request.icon)
                },
                """
                    insert into task.tasks_v2 
                        (created_at, modified_at, created_by, owned_by, title, body, progress, can_cancel, can_pause, icon, progress_percentage)
                    values 
                        (now(), now(), :username, :provider, :title, :body, :progress, :cancel, :pause, :icon, -1.0)
                    returning id
                """
            ).rows.singleOrNull()?.getLong(0) ?: throw RPCException(
                "Failed to create task",
                HttpStatusCode.InternalServerError
            )
        }

        return BackgroundTask(
            taskId = jobId,
            createdAt = initTime,
            modifiedAt = initTime,
            createdBy = request.user,
            provider = providerId,
            status = BackgroundTask.Status(
                state = TaskState.IN_QUEUE,
                title = request.title ?: DEFAULT_TASK_TITLE,
                body = request.body,
                progress = request.progress ?: DEFAULT_TASK_PROGRESS,
                progressPercentage = -1.0,
            ),
            specification = BackgroundTask.Specification(
                request.canPause,
                request.canCancel
            ),
            icon = request.icon
        )
    }

    private suspend fun updateBackgroundTask(actorAndProject: ActorAndProject, update: BackgroundTaskUpdate): BackgroundTask {
        return db.withSession { session ->
            val task = session
                .sendPreparedStatement(
                    {
                        setParameter("task_id", update.taskId)
                        setParameter("state", update.newStatus.state.toString())
                        setParameter("title", update.newStatus.title)
                        setParameter("body", update.newStatus.body)
                        setParameter("progress", update.newStatus.progress)
                        setParameter("progress_percentage", update.newStatus.progressPercentage)
                    },
                    """
                        update task.tasks_v2
                        set
                            modified_at = now(),
                            state = coalesce(:state, state),
                            title = coalesce(:title, title),
                            body = coalesce(:body, body),
                            progress = coalesce(:progress, progress),
                            progress_percentage = coalesce(:progress_percentage, progress_percentage)
                        where
                            id = :task_id
                            and not (state = 'SUCCESS' or state = 'FAILURE' or state='CANCELLED')
                        returning
                            id, extract(epoch from created_at)::bigint, extract(epoch from modified_at)::bigint,
                            created_by, owned_by, state, title, body, progress, progress_percentage, can_pause, can_cancel, icon
                    """
                )
                .rows
                .firstOrNull()
                ?.toBackgroundTask()

            if (task != null) {
                checkPermissions(actorAndProject, task)
            } else {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            task
        }
    }

    suspend fun pauseOrCancel(actorAndProject: ActorAndProject, request: PauseOrCancelRequest) {
        val taskId = request.id
        val foundTask = findById(actorAndProject, taskId)

        // NOTE(Henrik): If task is already finished all good.
        if (foundTask.status.state == TaskState.FAILURE || foundTask.status.state == TaskState.SUCCESS) return

        val newState = request.requestedState
        if (newState == TaskState.SUSPENDED && !foundTask.specification.canPause) {
            throw RPCException("Task cannot be paused", HttpStatusCode.BadRequest)
        }
        if (newState == TaskState.CANCELLED && !foundTask.specification.canCancel) {
            throw RPCException("Task cannot be cancelled", HttpStatusCode.BadRequest)
        }

        providerComm.call(
            foundTask.provider,
            actorAndProject,
            { TasksProvider(it).pauseOrCancel },
            request,
            isUserRequest = true
        )
    }

    suspend fun postStatus(actorAndProject: ActorAndProject, update: BackgroundTaskUpdate) {
        runCatching {
            val updatedTask = updateBackgroundTask(actorAndProject, update)
            subscriptionService.onTaskUpdate(updatedTask.createdBy, updatedTask)
        }
    }

    suspend fun markAsComplete(actorAndProject: ActorAndProject, taskId: Long) {
        val task = findById(actorAndProject, taskId)
        postStatus(
            actorAndProject,
            BackgroundTaskUpdate(
                taskId = taskId,
                newStatus = BackgroundTask.Status(
                    state = TaskState.SUCCESS,
                    title = task.status.title,
                    body = null,
                    progress = "Complete",
                    progressPercentage = 100.0
                ),
            )
        )
    }

    suspend fun browse(actorAndProject: ActorAndProject, request: BrowseRequest): PageV2<BackgroundTask> {
        val items = db.withSession { session ->
            val rows = session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                },
                """
                        select
                            id, extract(epoch from created_at)::bigint, extract(epoch from modified_at)::bigint,
                            created_by, owned_by, state, title, body, progress, progress_percentage, can_pause, can_cancel, icon
                        from task.tasks_v2 
                        where
                            created_by = :username
                            and state != 'SUCCESS'
                            and state != 'CANCELLED'
                            and state != 'FAILURE'
                            and modified_at >= now() - '24 hours'::interval
                        order by created_at desc
                        limit 100
                    """
            ).rows

            rows.map { it.toBackgroundTask() }
        }

        return PageV2.of(items)
    }

    suspend fun findById(actorAndProject: ActorAndProject, jobId: Long): BackgroundTask {
        val result = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("task_id", jobId)
                    },
                    """
                        select
                            id, extract(epoch from created_at)::bigint, extract(epoch from modified_at)::bigint,
                            created_by, owned_by, state, title, body, progress, progress_percentage, can_pause, can_cancel, icon
                        from task.tasks_v2 
                        where id = :task_id
                    """
                )
                .rows
                .map { it.toBackgroundTask() }
                .singleOrNull() ?: throw RPCException("Failed to find task", HttpStatusCode.NotFound)
        }

        checkPermissions(actorAndProject, result)
        return result
    }

    private fun checkPermissions(actorAndProject: ActorAndProject, task: BackgroundTask) {
        if (actorAndProject.actor == Actor.System) return
        val expectedProvider = task.provider
        val expectedUser = task.createdBy

        val username = actorAndProject.actor.safeUsername()
        if (username.startsWith(AuthProviders.PROVIDER_PREFIX)) {
            val providerId = username.removePrefix(AuthProviders.PROVIDER_PREFIX)
            if (expectedProvider != providerId) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
        } else {
            if (expectedUser != username) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
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
            getString(6),
            getString(7),
            getString(8),
            getFloat(9)?.toDouble()
        ),
        specification = BackgroundTask.Specification(
            getBoolean(10)!!,
            getBoolean(11)!!
        ),
        getString(12)
    )

    companion object : Loggable {
        override val log = logger()
    }
}
