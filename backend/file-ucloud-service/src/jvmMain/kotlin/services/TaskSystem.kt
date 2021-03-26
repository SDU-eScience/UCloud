package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.LongRunningTask
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Time
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import java.util.*
import kotlin.collections.ArrayList

data class StorageTask(
    val taskId: String,
    val requestName: String,
    val requirements: TaskRequirements,
    val rawRequest: JsonObject,
    val progress: JsonObject?,
    val owner: String,
    val lastUpdate: Long,
)

data class TaskRequirements(
    val scheduleInBackground: Boolean,
    val requirements: JsonObject
)

class TaskSystem {
    private val handlers = ArrayList<TaskHandler>()

    fun install(handler: TaskHandler) {
        handlers.add(handler)
    }

    suspend fun submitTask(actor: Actor, name: String, request: JsonObject): LongRunningTask<Unit> {
        val handler = handlers.find { it.canHandle(actor, name, request) }
            ?: throw RPCException("Unable to handle this request", HttpStatusCode.InternalServerError)

        val requirements = handler.collectRequirements(actor, name, request, REQUIREMENT_MAX_TIME_MS)
        if (requirements == null || requirements.scheduleInBackground) {
            return scheduleInBackground(actor, name, request, requirements)
        } else {
            val taskId = UUID.randomUUID().toString()
            handler.execute(
                actor,
                StorageTask(taskId, name, requirements, request, null, actor.safeUsername(), Time.now())
            )
            return LongRunningTask.Complete(Unit)
        }
    }

    private suspend fun scheduleInBackground(
        actor: Actor,
        name: String,
        request: JsonObject,
        requirements: TaskRequirements?,
    ): LongRunningTask.ContinuesInBackground<Unit> {
        return LongRunningTask.ContinuesInBackground(UUID.randomUUID().toString()) // TODO schedule it
    }

    companion object {
        const val REQUIREMENT_MAX_TIME_MS = 2000L
    }
}

interface TaskHandler {
    fun canHandle(actor: Actor, name: String, request: JsonObject): Boolean
    suspend fun collectRequirements(actor: Actor, name: String, request: JsonObject, maxTime: Long?): TaskRequirements?
    suspend fun execute(actor: Actor, task: StorageTask)
}