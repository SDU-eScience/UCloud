package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.coroutines.coroutineContext

// Note: These files are internal
@Serializable
private data class FileToCopy(
    override val oldPath: String,
    override val newPath: String,
    val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving

// Note: These files are internal
@Serializable
data class CopyTaskRequirements(
    val fileCount: Int?,
)

const val COPY_REQUIREMENTS_HARD_LIMIT = 10_000
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

        // Check permissions
        for (reqItem in realRequest.items) {
            /*
            aclService.requirePermission(actor, UCloudFile.create(reqItem.oldPath), FilePermission.READ)
            aclService.requirePermission(actor, UCloudFile.create(reqItem.newPath), FilePermission.WRITE)
             */
        }

        var fileCount = 0
        val deadline = if (maxTime == null) Time.now() + COPY_REQUIREMENTS_HARD_TIME_LIMIT else Time.now() + maxTime
        // TODO We need to check if the initial files even exist
        val pathStack = ArrayDeque(realRequest.items.map {
            FileToCopy(
                pathConverter.ucloudToInternal(UCloudFile.create(it.oldPath)).path,
                pathConverter.ucloudToInternal(UCloudFile.create(it.newPath)).path,
                it.conflictPolicy
            )
        })

        while (coroutineContext.isActive && (Time.now() <= deadline)) {
            if (fileCount >= COPY_REQUIREMENTS_HARD_LIMIT) break
            val nextItem = pathStack.removeFirstOrNull() ?: break
            fileCount++


            val nestedFiles = runCatching { nativeFs.listFiles(InternalFile(nextItem.oldPath)) }.getOrNull() ?: continue
            nestedFiles.forEach { fileName ->
                val oldPath = nextItem.oldPath + "/" + fileName
                val newPath = nextItem.newPath + "/" + fileName

                pathStack.add(FileToCopy(oldPath, newPath, WriteConflictPolicy.REPLACE))
            }
        }

        val hardLimitReached = fileCount >= COPY_REQUIREMENTS_HARD_LIMIT
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
                    pathConverter.ucloudToInternal(UCloudFile.create(reqItem.oldPath)).path,
                    pathConverter.ucloudToInternal(UCloudFile.create(reqItem.newPath)).path,
                    reqItem.conflictPolicy,
                )
            },
            doWork = doWork@{ nextItem ->
                val result = try {
                    nativeFs.copy(
                        InternalFile(nextItem.oldPath),
                        InternalFile(nextItem.newPath),
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
                        nativeFs.listFiles(InternalFile(nextItem.oldPath))
                    } catch (ex: FSException) {
                        emptyList()
                    }

                    for (childFileName in childrenFileNames) {
                        channel.send(
                            FileToCopy(
                                nextItem.oldPath + "/" + childFileName,
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
