package dk.sdu.cloud.plugins.storage.ucloud.tasks

import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.debug.detail
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FilesCreateFolderRequestItem
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.plugins.storage.ucloud.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.serviceContext
import dk.sdu.cloud.task.api.*
import kotlinx.serialization.json.JsonObject

class CreateFolderTask : TaskHandler {
    override fun TaskContext.canHandle(name: String, request: JsonObject): Boolean {
        return name == Files.createFolder.fullName && runCatching {
            defaultMapper.decodeFromJsonElement(BulkRequest.serializer(FilesCreateFolderRequestItem.serializer()), request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement(BulkRequest.serializer(FilesCreateFolderRequestItem.serializer()), request)

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement(BulkRequest.serializer(FilesCreateFolderRequestItem.serializer()), task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        runWork(
            backgroundDispatcher,
            numberOfCoroutines,
            realRequest.items,
            doWork = doWork@{ nextItem ->
                val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(nextItem.id))
                debug?.detail("Creating directory at: ${internalFile.path} (from ${nextItem.id})")
                try {
                    nativeFs.createDirectories(internalFile)
                } catch (ex: FSException) {
                    if (ex is FSException.AlreadyExists) return@doWork
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while creating folders: ${ex.stackTraceToString()}")
                    }
                    return@doWork
                }
            }
        )
    }

    override suspend fun TaskContext.postUpdate(taskId: Long, title: String?, body: String?, progress: String?, percentage: Double?) {
        Tasks.postStatus.call(
            PostStatusRequest(
                BackgroundTaskUpdate(
                    taskId = taskId,
                    modifiedAt = Time.now(),
                    newStatus = BackgroundTask.Status(
                        TaskState.RUNNING,
                        title,
                        body,
                        progress,
                        percentage
                    ),
                )
            ),
            serviceContext.rpcClient
        ).orThrow()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
