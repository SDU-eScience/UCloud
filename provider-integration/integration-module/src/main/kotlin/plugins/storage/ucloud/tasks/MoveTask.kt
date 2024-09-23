package dk.sdu.cloud.plugins.storage.ucloud.tasks

import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.rpcClient
import dk.sdu.cloud.plugins.storage.ucloud.*
import dk.sdu.cloud.plugins.storage.ucloud.tasks.CopyTask.Companion
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.serviceContext
import dk.sdu.cloud.task.api.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Note: These files are internal
@Serializable
private data class FileToMove(
    override val oldId: String,
    override val newId: String,
    val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving

class MoveTask : TaskHandler {
    override fun TaskContext.canHandle(name: String, request: JsonObject): Boolean {
        return name == Files.move.fullName && runCatching {
            defaultMapper.decodeFromJsonElement(BulkRequest.serializer(FilesMoveRequestItem.serializer()), request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val allPaths = ArrayList<UCloudFile>()
        val realRequest = defaultMapper.decodeFromJsonElement(BulkRequest.serializer(FilesMoveRequestItem.serializer()), request)
        for (reqItem in realRequest.items) {
            val source = UCloudFile.create(reqItem.oldId).also { allPaths.add(it) }
            val destination = UCloudFile.create(reqItem.newId).also { allPaths.add(it) }
            val oldId = pathConverter.ucloudToInternal(source).path
            val newId = pathConverter.ucloudToInternal(destination).path

            if (newId.startsWith("$oldId/")) {
                throw RPCException("Refusing to move a file to a sub-directory of itself.", HttpStatusCode.BadRequest)
            }
        }

        if (!pathConverter.shouldAllowUsageOfFilesTogether(allPaths)) {
            throw RPCException("This project does not allow copying files to other projects", HttpStatusCode.Forbidden)
        }

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement(BulkRequest.serializer(FilesMoveRequestItem.serializer()), task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        var filesMoved = 0L
        var filesToMove = realRequest.items.size
        runWork(
            backgroundDispatcher,
            numberOfCoroutines,
            realRequest.items.map {
                FileToMove(
                    pathConverter.ucloudToInternal(UCloudFile.create(it.oldId)).path,
                    pathConverter.ucloudToInternal(UCloudFile.create(it.newId)).path,
                    it.conflictPolicy
                )
            },
            doWork = doWork@{ nextItem ->
                // NOTE(Dan): This resolves the drive just to be sure we haven't entered maintenance mode
                pathConverter.locator.resolveDriveByInternalFile(InternalFile(nextItem.oldId))
                pathConverter.locator.resolveDriveByInternalFile(InternalFile(nextItem.newId))

                try {
                    val needsToRecurse = nativeFs.move(
                        InternalFile(nextItem.oldId),
                        InternalFile(nextItem.newId),
                        nextItem.conflictPolicy
                    ).needsToRecurse

                    filesMoved++
                    if (filesMoved % 100L == 0L) {
                        try {
                            postUpdate(
                                task.taskId.toLong(),
                                "Moving Files",
                                "$filesMoved/$filesToMove files have been moved"
                            )
                        } catch (ex: Exception) {
                            CopyTask.log.warn("Failed to update status for task: $task")
                            CopyTask.log.info(ex.message)
                        }
                    }

                    if (needsToRecurse) {
                        val childrenFileNames = runCatching { nativeFs.listFiles(InternalFile(nextItem.oldId)) }
                            .getOrDefault(emptyList())
                        filesToMove += childrenFileNames.size
                        for (childFileName in childrenFileNames) {
                            channel.send(
                                FileToMove(
                                    nextItem.oldId + "/" + childFileName,
                                    nextItem.newId + "/" + childFileName,
                                    nextItem.conflictPolicy
                                )
                            )
                        }
                    }
                } catch (ex: FSException) {
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while deleting files: ${ex.stackTraceToString()}")
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
