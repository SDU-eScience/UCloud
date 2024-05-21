package dk.sdu.cloud.plugins.storage.ucloud.tasks

import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.storage.ucloud.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

// Note: These files are internal
@Serializable
private data class FileToCopy(
    override val oldId: String,
    override val newId: String,
    val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving

// Note: These files are internal
@Serializable
data class CopyTaskRequirements(
    val fileCount: Int?,
)

const val COPY_REQUIREMENTS_HARD_LIMIT = 100
const val COPY_REQUIREMENTS_SIZE_HARD_LIMIT = 1000 * 1000 * 100
const val COPY_REQUIREMENTS_HARD_TIME_LIMIT = 2000

class CopyTask : TaskHandler {
    override fun TaskContext.canHandle(name: String, request: JsonObject): Boolean {
        return name == Files.copy.fullName && runCatching {
            defaultMapper.decodeFromJsonElement(BulkRequest.serializer(FilesCopyRequestItem.serializer()), request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement(
            BulkRequest.serializer(FilesCopyRequestItem.serializer()),
            request
        )

        val allPaths = ArrayList<UCloudFile>()
        val deadline = if (maxTime == null) Time.now() + COPY_REQUIREMENTS_HARD_TIME_LIMIT else Time.now() + maxTime
        val pathStack = ArrayDeque(realRequest.items.map {
            val source = UCloudFile.create(it.oldId).also { allPaths.add(it) }
            val destination = UCloudFile.create(it.newId).also { allPaths.add(it) }
            val oldId = pathConverter.ucloudToInternal(source).path
            val newId = pathConverter.ucloudToInternal(destination).path

            if (newId.startsWith("$oldId/")) {
                throw RPCException("Refusing to copy a file to a sub-directory of itself.", HttpStatusCode.BadRequest)
            }

            FileToCopy(oldId, newId, it.conflictPolicy)
        })
        var fileCount = pathStack.size
        var fileSize = 0L

        if (!pathConverter.shouldAllowUsageOfFilesTogether(allPaths)) {
            throw RPCException("This project does not allow copying files to other projects", HttpStatusCode.Forbidden)
        }

        while (coroutineContext.isActive && (Time.now() <= deadline)) {
            if (fileCount >= COPY_REQUIREMENTS_HARD_LIMIT) break
            if (fileSize >= COPY_REQUIREMENTS_SIZE_HARD_LIMIT) break
            val nextItem = pathStack.removeFirstOrNull() ?: break

            val stat = runCatching { nativeFs.stat(InternalFile((nextItem.oldId))) }.getOrNull() ?: continue
            fileSize += stat.size

            if (stat.fileType == FileType.DIRECTORY) {
                val nestedFiles =
                    runCatching { nativeFs.listFiles(InternalFile(nextItem.oldId)) }.getOrNull() ?: continue
                fileCount += nestedFiles.size
                nestedFiles.forEach { fileName ->
                    val oldPath = nextItem.oldId + "/" + fileName
                    val newPath = nextItem.newId + "/" + fileName

                    pathStack.add(FileToCopy(oldPath, newPath, WriteConflictPolicy.REPLACE))
                }
            }
        }

        val hardLimitReached = fileCount >= COPY_REQUIREMENTS_HARD_LIMIT || fileSize >= COPY_REQUIREMENTS_SIZE_HARD_LIMIT
        return TaskRequirements(
            hardLimitReached,
            defaultMapper.encodeToJsonElement(
                CopyTaskRequirements.serializer(),
                CopyTaskRequirements(if (hardLimitReached) -1 else fileCount)
            ) as JsonObject
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement(BulkRequest.serializer(FilesCopyRequestItem.serializer()), task.rawRequest)

        val numberOfCoroutines = if (task.requirements?.scheduleInBackground == true) 10 else 1
        runWork(
            backgroundDispatcher,
            numberOfCoroutines,
            realRequest.items.map { reqItem ->
                FileToCopy(
                    pathConverter.ucloudToInternal(UCloudFile.create(reqItem.oldId)).path,
                    pathConverter.ucloudToInternal(UCloudFile.create(reqItem.newId)).path,
                    reqItem.conflictPolicy,
                )
            },
            doWork = doWork@{ nextItem ->
                // NOTE(Dan): This resolves the drive just to be sure we haven't entered maintenance mode
                pathConverter.locator.resolveDriveByInternalFile(InternalFile(nextItem.oldId))
                pathConverter.locator.resolveDriveByInternalFile(InternalFile(nextItem.newId))

                val result = try {
                    nativeFs.copy(
                        InternalFile(nextItem.oldId),
                        InternalFile(nextItem.newId),
                        nextItem.conflictPolicy
                    )
                } catch (ex: FSException) {
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while copying files: ${ex.stackTraceToString()}")
                    }
                    return@doWork
                }

                if (result is CopyResult.CreatedDirectory) {
                    val outputFile = result.outputFile
                    val childrenFileNames = try {
                        nativeFs.listFiles(InternalFile(nextItem.oldId))
                    } catch (ex: FSException) {
                        emptyList()
                    }

                    for (childFileName in childrenFileNames) {
                        channel.send(
                            FileToCopy(
                                nextItem.oldId + "/" + childFileName,
                                outputFile.path + "/" + childFileName,
                                nextItem.conflictPolicy
                            )
                        )
                    }

                }
            }
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
