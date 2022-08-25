package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class EmptyTrashRequestItem(
    val username: String,
    val path: String
)

class EmptyTrashTask: TaskHandler {
    override fun TaskContext.canHandle(name: String, request: JsonObject): Boolean {
        return name == Files.emptyTrash.fullName && runCatching {
            defaultMapper.decodeFromJsonElement<BulkRequest<EmptyTrashRequestItem>>(request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<BulkRequest<EmptyTrashRequestItem>>(request)

        return TaskRequirements(true, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<BulkRequest<EmptyTrashRequestItem>>(task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1

        runWork(
            backgroundScope.dispatcher,
            numberOfCoroutines,
            realRequest.items,
            doWork = doWork@{ nextItem ->
                val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(nextItem.path))
                try {
                    nativeFs.delete(internalFile)
                    nativeFs.createDirectories(internalFile)
                } catch (ex: FSException) {
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while deleting files during emptying of trash: ${ex.stackTraceToString()}")
                    }
                    return@doWork
                }

            }
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
