package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.Actor
import kotlinx.serialization.json.JsonObject

data class StorageTask(
    val taskId: String,
    val requestName: String,
    val requirements: JsonObject,
    val rawRequest: JsonObject,
    val progress: JsonObject,
    val owner: String,
    val lastUpdate: Long,
)

class TaskSystem {

}

interface TaskHandler {
    fun canHandle(actor: Actor, name: String, request: JsonObject): Boolean
    suspend fun collectRequirements(actor: Actor, name: String, request: JsonObject, maxTime: Long?): JsonObject
    suspend fun execute(actor: Actor, task: StorageTask)
}

class TaskDeadlineExceededException() : RuntimeException(
    "Took too long to collect requirements. Forced into background."
)