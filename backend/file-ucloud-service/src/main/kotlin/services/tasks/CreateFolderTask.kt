package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.debug.detail
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FilesCreateFolderRequest
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class CreateFolderTask : TaskHandler {
    override fun TaskContext.canHandle(name: String, request: JsonObject): Boolean {
        return name == Files.createFolder.fullName && runCatching {
            defaultMapper.decodeFromJsonElement<FilesCreateFolderRequest>(request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesCreateFolderRequest>(request)

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesCreateFolderRequest>(task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        runWork(
            backgroundScope.dispatcher,
            numberOfCoroutines,
            realRequest.items,
            doWork = doWork@{ nextItem ->
                val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(nextItem.id))
                debug.detail("Creating directory at: ${internalFile.path} (from ${nextItem.id})")
                try {
                    nativeFs.createDirectories(internalFile)
                } catch (ex: FSException) {
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while creating folders: ${ex.stackTraceToString()}")
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
