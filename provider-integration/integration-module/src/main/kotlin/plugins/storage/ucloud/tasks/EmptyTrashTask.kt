package dk.sdu.cloud.plugins.storage.ucloud.tasks

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.child
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.plugins.storage.ucloud.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.task.api.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.Path

@Serializable
data class EmptyTrashRequestItem(
    val username: String,
    val path: String
)

class EmptyTrashTask(
    private val fs: NativeFS,
    private val stagingFolder: InternalFile?
) : TaskHandler {
    /*
    init {
        if (stagingFolder != null) {
            ProcessingScope.launch {
                val taskName = "empty-trash-staging"
                while (isActive) {
                    val start = Time.now()
                    Prometheus.countBackgroundTask(taskName)
                    try {
                        val files = fs.listFiles(stagingFolder)
                        files.forEach {
                            fs.delete(InternalFile(stagingFolder.path + "/" + it))
                        }
                    } catch (ex: Throwable) {
                        log.warn("Caught exception while emptying trash (staging): ${ex.toReadableStacktrace()}")
                    } finally {
                        val duration = Time.now() - start
                        Prometheus.measureBackgroundDuration(taskName, duration)
                        delay(60_000 - duration)
                    }
                }
            }
        }
    }
     */

    override fun TaskContext.canHandle(name: String, request: JsonObject): Boolean {
        return name == Files.emptyTrash.fullName && runCatching {
            defaultMapper.decodeFromJsonElement(BulkRequest.serializer(EmptyTrashRequestItem.serializer()), request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest =
            defaultMapper.decodeFromJsonElement(BulkRequest.serializer(EmptyTrashRequestItem.serializer()), request)

        return TaskRequirements(true, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement(
            BulkRequest.serializer(EmptyTrashRequestItem.serializer()),
            task.rawRequest
        )

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1

        runWork(
            backgroundDispatcher,
            numberOfCoroutines,
            realRequest.items,
            doWork = doWork@{ nextItem ->
                val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(nextItem.path))

                val driveInfo = pathConverter.locator.resolveDriveByInternalFile(internalFile)
                val info = pathConverter.locator.fetchMetadataForDrive(driveInfo.drive.ucloudId) ?: return@doWork

                val drives = pathConverter.locator.listDrivesByWorkspace(workspace = info.workspace)

                try {
                    if (stagingFolder == null) {
                        nativeFs.delete(internalFile)
                        nativeFs.createDirectories(internalFile)
                    } else {
                        val filesToDelete = nativeFs.listFiles(internalFile)
                        var filesDeleted = 0L
                        filesToDelete.forEach {
                            try {
                                postUpdate(
                                    task.taskId.toLong(),
                                    "Emptying Trash",
                                    "$filesDeleted/${filesToDelete.size} deleted"
                                )
                            } catch (ex: Exception) {
                                log.warn("Failed to update status for task: $task")
                                log.info(ex.message)
                            }
                            try {
                                val src = internalFile.child(it)
                                val dst = stagingFolder.child(UUID.randomUUID().toString())
                                java.nio.file.Files.move(
                                    Path(src.path),
                                    Path(dst.path),
                                    StandardCopyOption.REPLACE_EXISTING
                                )
                                fs.move(src, dst, WriteConflictPolicy.RENAME)
                            } catch (ignored: FSException.NotFound) {}
                        }
                    }

                    for (drive in drives) {
                        usageScan.requestScan(drive.drive.ucloudId)
                    }
                } catch (ex: FSException) {
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while deleting files during emptying of trash: ${ex.stackTraceToString()}")
                    }
                    return@doWork
                }
            }
        )
    }

    override suspend fun TaskContext.postUpdate(taskId: Long, operation: String, progress: String) {
        Tasks.postStatus.call(
            PostStatusRequest(
                BackgroundTaskUpdate(
                    taskId = taskId,
                    modifiedAt = Time.now(),
                    newStatus = BackgroundTask.Status(
                        TaskState.RUNNING,
                        operation,
                        progress
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
