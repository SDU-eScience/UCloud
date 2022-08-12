package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FilesControl
import dk.sdu.cloud.file.orchestrator.api.FilesControlAddUpdateRequestItem
import dk.sdu.cloud.file.orchestrator.api.FilesControlMarkAsCompleteRequestItem
import dk.sdu.cloud.file.orchestrator.api.LongRunningTask
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
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
    private val db: DBContext,
    private val pathConverter: PathConverter,
    private val nativeFs: NativeFS,
    private val backgroundDispatcher: CoroutineDispatcher,
    private val client: AuthenticatedClient,
    private val debug: DebugSystem?,
) {
    private val taskContext = TaskContext(pathConverter, nativeFs, backgroundDispatcher, debug)
    private val handlers = ArrayList<TaskHandler>()

    fun install(handler: TaskHandler) {
        handlers.add(handler)
    }

    suspend fun submitTask(name: String, request: JsonObject): LongRunningTask {
        val handler = handlers.find { with(it) { taskContext.canHandle(name, request) } } ?: run {
            log.warn("Unable to handle request: $name $request")
            throw RPCException("Unable to handle this request", HttpStatusCode.InternalServerError)
        }

        with (handler) {
            val requirements = taskContext.collectRequirements(name, request, REQUIREMENT_MAX_TIME_MS)
            return if (requirements == null || requirements.scheduleInBackground) {
                scheduleInBackground(name, request, requirements)
            } else {
                val taskId = UUID.randomUUID().toString()
                taskContext.execute(
                    StorageTask(taskId, name, requirements, request, null, Time.now())
                )
                LongRunningTask.Complete()
            }
        }
    }

    private suspend fun scheduleInBackground(
        name: String,
        request: JsonObject,
        requirements: TaskRequirements?,
    ): LongRunningTask.ContinuesInBackground {
        val id = UUID.randomUUID().toString()
        db.withSession { session ->
            session.prepareStatement(
                """
                    insert into file_ucloud.tasks
                    (id, request_name, requirements, request, progress, last_update, processor_id) 
                    values (:id, :request_name, :requirements, :request, :progress, null, null) 
                """
            ).useAndInvokeAndDiscard {
                bindString("id", id)
                bindString("request_name", name)
                bindString("request", defaultMapper.encodeToString(JsonObject.serializer(), request))
                bindString("requirements", defaultMapper.encodeToString(TaskRequirements.serializer().nullable, requirements))
                bindStringNullable("progress", null as String?)
            }
        }
        return LongRunningTask.ContinuesInBackground(id)
    }

    fun launchScheduler(scope: CoroutineScope) {
        repeat(1) {
            scope.launch {
                while (isActive) {
                    var taskInProgress: String? = null
                    try {
                        val task = db.withSession { session ->
                            val processorAndTask = run {
                                val rows = ArrayList<Pair<String, StorageTask>>()
                                session
                                    .prepareStatement(
                                        """
                                            select processor_id, id, request_name, requirements, request, progress,
                                                   last_update
                                            from file_ucloud.tasks 
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
                                                    row.getString(0)!!,
                                                    StorageTask(
                                                        row.getString(1)!!,
                                                        row.getString(2)!!,
                                                        row.getString(3)?.let { defaultMapper.decodeFromString(TaskRequirements.serializer(), it) },
                                                        row.getString(4)!!.let { defaultMapper.decodeFromString(JsonObject.serializer(), it) },
                                                        row.getString(5)?.let { defaultMapper.decodeFromString(JsonObject.serializer(), it) },
                                                        row.getLong(6)!!
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
                                        update file_ucloud.tasks 
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
                                        bindString("last_processor", oldProcessor)
                                        bindString("id", task.taskId)
                                    },
                                    readRow = { success = true}
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
                            continue
                        }

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
                            FilesControl.addUpdate.call(
                                bulkRequestOf(
                                    FilesControlAddUpdateRequestItem(
                                        task.taskId,
                                        "Resuming work on task..."
                                    )
                                ),
                                client
                            )
                            taskContext.execute(task.copy(requirements = requirements))
                            FilesControl.markAsComplete.call(
                                bulkRequestOf(
                                    FilesControlMarkAsCompleteRequestItem(task.taskId)
                                ),
                                client
                            )
                            log.debug("Completed the execution of $task")

                            markJobAsComplete(db, task.taskId)
                        }
                    } catch (ex: Throwable) {
                        log.warn("Execution of task failed!\n${ex.stackTraceToString()}")
                        if (taskInProgress != null) markJobAsComplete(db, taskInProgress)
                    }
                }
            }
        }
    }

    private suspend fun markJobAsComplete(ctx: DBContext, id: String) {
        ctx.withSession { session ->
            session.prepareStatement(
                "update file_ucloud.tasks set complete = true where id = :id"
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
    val pathConverter: PathConverter,
    val nativeFs: NativeFS,
    val backgroundDispatcher: CoroutineDispatcher,
    val debug: DebugSystem?
)

interface TaskHandler {
    fun TaskContext.canHandle(name: String, request: JsonObject): Boolean
    suspend fun TaskContext.collectRequirements(name: String, request: JsonObject, maxTime: Long?): TaskRequirements?
    suspend fun TaskContext.execute(task: StorageTask)
}
