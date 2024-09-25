package dk.sdu.cloud.plugins.storage.ucloud.tasks

import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.fileName
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.plugins.storage.ucloud.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.serviceContext
import dk.sdu.cloud.task.api.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TrashRequestItem(
    val username: String,
    val path: String
)

class TrashTask(
    private val memberFiles: MemberFiles,
    private val trashService: TrashService,
) : TaskHandler {
    override fun TaskContext.canHandle(name: String, request: JsonObject): Boolean {
        return name == Files.trash.fullName && runCatching {
            defaultMapper.decodeFromJsonElement(BulkRequest.serializer(TrashRequestItem.serializer()), request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement(BulkRequest.serializer(TrashRequestItem.serializer()), request)

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement(BulkRequest.serializer(TrashRequestItem.serializer()), task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        var filesMoved = 0L
        runWork(
            backgroundDispatcher,
            numberOfCoroutines,
            realRequest.items,
            doWork = doWork@{ nextItem ->
                if ((filesMoved % 100) == 0L) {
                    try {
                        postUpdate(
                            task.taskId.toLong(),
                            "Moving files to Trash",
                            null,
                            "$filesMoved/${realRequest.items.size} moved to trash",
                            (filesMoved.toDouble() / realRequest.items.size.toDouble()) * 100.0
                        )
                    } catch (ex: Exception) {
                        log.warn("Failed to update status for task: $task")
                        log.info(ex.message)
                    }
                }
                try {
                    val file = UCloudFile.create(nextItem.path)
                    val internalFile = pathConverter.ucloudToInternal(file)
                    val (drive) = pathConverter.locator.resolveDriveByInternalFile(internalFile)
                    val project = drive.project
                    val targetDirectory: InternalFile = trashService.findTrashDirectory(nextItem.username, internalFile)
                    val targetFile = InternalFile(targetDirectory.path + "/" + internalFile.fileName())
                    if (project != null) {
                        memberFiles.initializeMemberFiles(nextItem.username, project)
                    }

                    try {
                        nativeFs.stat(targetDirectory)
                    } catch (ex: FSException.NotFound) {
                        nativeFs.createDirectories(targetDirectory)
                    }

                    nativeFs.move(
                        internalFile,
                        targetFile,
                        WriteConflictPolicy.RENAME,
                        updateTimestamps = true,
                    )
                    filesMoved++
                } catch (ex: FSException) {
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while deleting files: ${ex.stackTraceToString()}")
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
