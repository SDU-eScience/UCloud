package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.Actor
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FilesDeleteRequest
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class DeleteTask : TaskHandler {
    override fun TaskContext.canHandle(name: String, request: JsonObject): Boolean {
        return name == Files.delete.fullName && runCatching {
            defaultMapper.decodeFromJsonElement<FilesDeleteRequest>(request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesDeleteRequest>(request)
        for (reqItem in realRequest.items) {
            // TODO There is a fast-path we could optimize where all files are of a single parent and we have write
            //   permissions in that parent
            // aclService.requirePermission(actor, UCloudFile.create(reqItem.path), FilePermission.WRITE)
        }

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesDeleteRequest>(task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        runWork(
            backgroundScope.dispatcher,
            numberOfCoroutines,
            realRequest.items,
            doWork = doWork@{ nextItem ->
                val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(nextItem.path))
                try {
                    nativeFs.delete(internalFile)
                } catch (ex: FSException) {
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while deleting files: ${ex.stackTraceToString()}")
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
