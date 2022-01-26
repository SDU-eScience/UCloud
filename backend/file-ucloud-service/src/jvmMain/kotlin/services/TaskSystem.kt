package dk.sdu.cloud.file.ucloud.services

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
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
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
    private val backgroundScope: BackgroundScope,
    private val client: AuthenticatedClient,
    private val debug: DebugSystem
) {
    private val taskContext = TaskContext(pathConverter, nativeFs, backgroundScope, debug)
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
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("request_name", name)
                    setParameter("request", defaultMapper.encodeToString(request))
                    setParameter("requirements", defaultMapper.encodeToString(requirements))
                    setParameter("progress", null as String?)
                },
                """
                    insert into file_ucloud.tasks
                    (id, request_name, requirements, request, progress, last_update, processor_id) 
                    values (:id, :request_name, :requirements, :request, :progress, null, null) 
                """
            )
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
                            val processorAndTask = session
                                .sendPreparedStatement(
                                    { setParameter("processor_id", processorId) },
                                    """
                                        select * from file_ucloud.tasks 
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
                                .rows
                                .map {
                                    Pair(
                                        it.getString("processor_id"),

                                        StorageTask(
                                            it.getString("id")!!,
                                            it.getString("request_name")!!,
                                            it.getString("requirements")?.let { defaultMapper.decodeFromString(it) },
                                            it.getString("request")!!.let { defaultMapper.decodeFromString(it) },
                                            it.getString("progress")?.let { defaultMapper.decodeFromString(it) },
                                            it.getAs<LocalDateTime?>("last_update")
                                                ?.toDateTime(DateTimeZone.UTC)?.millis ?: 0L,
                                        )
                                    )
                                }
                                .singleOrNull()

                            if (processorAndTask == null) {
                                null
                            } else {
                                val (oldProcessor, task) = processorAndTask

                                val success = session.sendPreparedStatement(
                                    {
                                        setParameter("processor_id", processorId)
                                        setParameter("last_processor", oldProcessor)
                                        setParameter("id", task.taskId)
                                    },
                                    """
                                        update file_ucloud.tasks 
                                        set processor_id = :processor_id 
                                        where 
                                            id = :id and
                                            (
                                                (processor_id is null and :last_processor::text is null) or 
                                                processor_id = :last_processor
                                            )
                                    """
                                ).rowsAffected == 1L

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
            session.sendPreparedStatement(
                { setParameter("id", id) },
                "update file_ucloud.tasks set complete = true where id = :id"
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
    val backgroundScope: BackgroundScope,
    val debug: DebugSystem
)

interface TaskHandler {
    fun TaskContext.canHandle(name: String, request: JsonObject): Boolean
    suspend fun TaskContext.collectRequirements(name: String, request: JsonObject, maxTime: Long?): TaskRequirements?
    suspend fun TaskContext.execute(task: StorageTask)
}