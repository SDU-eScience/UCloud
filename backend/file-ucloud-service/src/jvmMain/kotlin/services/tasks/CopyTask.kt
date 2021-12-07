package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.coroutines.coroutineContext

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
            defaultMapper.decodeFromJsonElement<FilesCopyRequest>(request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesCopyRequest>(request)

        val deadline = if (maxTime == null) Time.now() + COPY_REQUIREMENTS_HARD_TIME_LIMIT else Time.now() + maxTime
        val pathStack = ArrayDeque(realRequest.items.map {
            val oldId = pathConverter.ucloudToInternal(UCloudFile.create(it.oldId)).path
            val newId = pathConverter.ucloudToInternal(UCloudFile.create(it.newId)).path

            if (newId.startsWith("$oldId/")) {
                throw RPCException("Refusing to copy a file to a sub-directory of itself.", HttpStatusCode.BadRequest)
            }

            FileToCopy(oldId, newId, it.conflictPolicy)
        })
        var fileCount = pathStack.size
        var fileSize = 0L

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
                CopyTaskRequirements(if (hardLimitReached) -1 else fileCount)
            ) as JsonObject
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesCopyRequest>(task.rawRequest)

        val numberOfCoroutines = if (task.requirements?.scheduleInBackground == true) 10 else 1
        runWork(
            backgroundScope.dispatcher,
            numberOfCoroutines,
            realRequest.items.map { reqItem ->
                FileToCopy(
                    pathConverter.ucloudToInternal(UCloudFile.create(reqItem.oldId)).path,
                    pathConverter.ucloudToInternal(UCloudFile.create(reqItem.newId)).path,
                    reqItem.conflictPolicy,
                )
            },
            doWork = doWork@{ nextItem ->
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
