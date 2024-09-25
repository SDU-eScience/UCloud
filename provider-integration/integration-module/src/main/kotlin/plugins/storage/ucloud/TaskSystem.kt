package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.Prometheus
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.debug.DebugContextType
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import dk.sdu.cloud.task.api.*
import dk.sdu.cloud.utils.whileGraal
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonObject
import java.util.*
import kotlin.collections.ArrayList

data class StorageTask(
    val taskId: String,
    val requestName: String,
    val requirements: TaskRequirements?,
    val rawRequest: JsonObject,
    val progress: JsonObject?,
    val lastUpdate: Long,
)

@Serializable
data class TaskRequirements(
    val scheduleInBackground: Boolean,
    val requirements: JsonObject,
)

class TaskSystem(
    private val pluginConfig: ConfigSchema.Plugins.Files.UCloud,
    private val db: DBContext,
    private val pathConverter: PathConverter,
    private val nativeFs: NativeFS,
    private val backgroundDispatcher: CoroutineDispatcher,
    private val client: AuthenticatedClient,
    private val debug: DebugSystem,
    private val usageScan: UsageScan,
) {
    private val taskContext = TaskContext(pluginConfig, pathConverter, nativeFs, backgroundDispatcher, debug,
        usageScan)
    private val handlers = ArrayList<TaskHandler>()

    fun install(handler: TaskHandler) {
        handlers.add(handler)
    }

    suspend fun submitTask(
        requestName: String,
        request: JsonObject,
        username: String,
        title: String,
        body: String?,
        progressDescription: String = "Accepted",
        canPause: Boolean = false,
        canCancel: Boolean = false,
        icon: String?
    ): BackgroundTask {
        val handler = handlers.find { with(it) { taskContext.canHandle(requestName, request) } } ?: run {
            log.warn("Unable to handle request: $requestName $request")
            throw RPCException("Unable to handle this request", HttpStatusCode.InternalServerError)
        }

        with (handler) {
            val requirements = taskContext.collectRequirements(requestName, request, REQUIREMENT_MAX_TIME_MS)
            val task = Tasks.create.call(
                CreateRequest(
                    user = username,
                    title = title,
                    body = body,
                    progress = progressDescription,
                    canPause = canPause,
                    canCancel = canCancel,
                    icon = icon
                ),
                client
            ).orThrow()
            return if (requirements == null || requirements.scheduleInBackground) {
                scheduleInBackground(requestName, request, requirements, task)
            } else {

                taskContext.execute(
                    StorageTask(
                        task.taskId.toString(),
                        requestName,
                        requirements,
                        request,
                        null,
                        Time.now()
                    )
                )

                Tasks.postStatus.call(
                    PostStatusRequest(
                        BackgroundTaskUpdate(
                            task.taskId,
                            Time.now(),
                            BackgroundTask.Status(
                                TaskState.SUCCESS,
                                task.status.title,
                                null,
                                "Done",
                                100.0
                            )
                        )
                    ),
                    client
                ).orThrow()

                task
            }
        }
    }

    private suspend fun scheduleInBackground(
        requestName: String,
        request: JsonObject,
        requirements: TaskRequirements?,
        task: BackgroundTask
    ): BackgroundTask {
        db.withSession { session ->
            session.prepareStatement(
                """
                    insert into ucloud_storage_tasks
                    (id, request_name, requirements, request, progress, last_update, processor_id) 
                    values (:id, :request_name, :requirements, :request, :progress, null, null) 
                """
            ).useAndInvokeAndDiscard {
                bindString("id", task.taskId.toString())
                bindString("request_name", requestName)
                bindString("request", defaultMapper.encodeToString(JsonObject.serializer(), request))
                bindString("requirements", defaultMapper.encodeToString(TaskRequirements.serializer().nullable, requirements))
                bindStringNullable("progress", null as String?)
            }
        }
        return task
    }

    fun launchScheduler(scope: CoroutineScope) {
        scope.launch {
            whileGraal({ isActive }) {
                val prometheusTaskName = "file_task_scheduling"
                debug.useContext(DebugContextType.BACKGROUND_TASK, "File background task", MessageImportance.IMPLEMENTATION_DETAIL) {
                    var taskInProgress: String? = null
                    val start = Time.now()
                    try {
                        val task = db.withSession { session ->
                            val processorAndTask = run {
                                val rows = ArrayList<Pair<String?, StorageTask>>()
                                session
                                    .prepareStatement(
                                        """
                                        select processor_id, id, request_name, requirements, request, progress,
                                               last_update
                                        from ucloud_storage_tasks 
                                        where 
                                            complete = false and
                                            (
                                                last_update is null or 
                                                last_update < (now() - interval '5 minutes')
                                            ) and
                                            (processor_id is null or processor_id != :processor_id)
                                        limit 1
                                    """
                                    )
                                    .useAndInvoke(
                                        prepare = {
                                            bindString("processor_id", processorId)
                                        },
                                        readRow = { row ->
                                            rows.add(
                                                Pair(
                                                    row.getString(0),
                                                    StorageTask(
                                                        row.getString(1)!!,
                                                        row.getString(2)!!,
                                                        row.getString(3)?.let {
                                                            defaultMapper.decodeFromString(
                                                                TaskRequirements.serializer(),
                                                                it
                                                            )
                                                        },
                                                        row.getString(4)!!.let {
                                                            defaultMapper.decodeFromString(
                                                                JsonObject.serializer(),
                                                                it
                                                            )
                                                        },
                                                        row.getString(5)?.let {
                                                            defaultMapper.decodeFromString(
                                                                JsonObject.serializer(),
                                                                it
                                                            )
                                                        },
                                                        row.getLong(6) ?: 0L
                                                    )
                                                )
                                            )
                                        }
                                    )

                                rows.singleOrNull()
                            }

                            if (processorAndTask == null) {
                                null
                            } else {
                                val (oldProcessor, task) = processorAndTask

                                var success = false
                                session.prepareStatement(
                                    """
                                    update ucloud_storage_tasks 
                                    set processor_id = :processor_id 
                                    where 
                                        id = :id and
                                        (
                                            (processor_id is null and :last_processor::text is null) or 
                                            processor_id = :last_processor
                                        )
                                    returning id
                                """
                                ).useAndInvoke(
                                    prepare = {
                                        bindString("processor_id", processorId)
                                        bindStringNullable("last_processor", oldProcessor)
                                        bindString("id", task.taskId)
                                    },
                                    readRow = { success = true }
                                )

                                if (!success) {
                                    null
                                } else {
                                    task
                                }
                            }
                        }

                        if (task == null) {
                            delay(10_000)
                            return@useContext
                        }

                        Prometheus.countBackgroundTask(prometheusTaskName)
                        taskInProgress = task.taskId

                        val handler = handlers.find {
                            with(it) {
                                taskContext.canHandle(task.requestName, task.rawRequest)
                            }
                        } ?: run {
                            log.warn("Unable to handle request: ${task}")
                            throw RPCException("Unable to handle this request", HttpStatusCode.InternalServerError)
                        }

                        with(handler) {
                            val requirements = task.requirements
                                ?: (taskContext.collectRequirements(task.requestName, task.rawRequest, null)
                                    ?: error("Handler returned no requirements $task"))

                            log.debug("Starting work of $task")

                            taskContext.execute(task.copy(requirements = requirements))

                            try {
                                Tasks.markAsComplete.call(
                                    MarkAsCompleteRequest(
                                        task.taskId.toLong()
                                    ),
                                    client
                                )
                            } catch (ex: Exception) {
                                log.warn("Failed to mark task (${task.taskId}) as completed")
                                log.info(ex.message)
                                return@useContext
                            }
                            log.debug("Completed the execution of $task")

                            markJobAsComplete(db, task.taskId)
                        }
                    } catch (ex: Throwable) {
                        log.warn("Execution of task failed!\n${ex.stackTraceToString()}")
                        if (taskInProgress != null) markJobAsComplete(db, taskInProgress)
                    } finally {
                        if (taskInProgress != null) Prometheus.measureBackgroundDuration(prometheusTaskName, Time.now() - start)
                    }
                }
            }
        }
    }

    private suspend fun markJobAsComplete(ctx: DBContext, id: String) {
        ctx.withSession { session ->
            session.prepareStatement(
                "update ucloud_storage_tasks set complete = true where id = :id"
            ).useAndInvokeAndDiscard(
                prepare = { bindString("id", id) },
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
        const val REQUIREMENT_MAX_TIME_MS = 2000L
        val processorId = UUID.randomUUID().toString()
    }
}

data class TaskContext(
    val config: ConfigSchema.Plugins.Files.UCloud,
    val pathConverter: PathConverter,
    val nativeFs: NativeFS,
    val backgroundDispatcher: CoroutineDispatcher,
    val debug: DebugSystem?,
    val usageScan: UsageScan,
)

interface TaskHandler {
    fun TaskContext.canHandle(name: String, request: JsonObject): Boolean
    suspend fun TaskContext.postUpdate(taskId: Long, title: String?, body: String?, progress: String?, percentage: Double?)
    suspend fun TaskContext.collectRequirements(name: String, request: JsonObject, maxTime: Long?): TaskRequirements?
    suspend fun TaskContext.execute(task: StorageTask)
}
