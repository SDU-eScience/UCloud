package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.acl.AclService
import dk.sdu.cloud.file.ucloud.services.acl.requirePermission
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.util.concurrent.atomic.AtomicInteger
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

class CopyTask(
    private val aclService: AclService,
    private val queries: PathConverter,
    private val backgroundScope: BackgroundScope,
) : TaskHandler {
    override fun canHandle(actor: Actor, name: String, request: JsonObject): Boolean {
        return name == Files.copy.fullName && runCatching {
            defaultMapper.decodeFromJsonElement<FilesCopyRequest>(request)
        }.isSuccess
    }

    override suspend fun collectRequirements(
        actor: Actor,
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesCopyRequest>(request)

        // Check permissions
        for (reqItem in realRequest.items) {
            aclService.requirePermission(actor, UCloudFile.create(reqItem.oldPath), FilePermission.READ)
            aclService.requirePermission(actor, UCloudFile.create(reqItem.newPath), FilePermission.WRITE)
        }

        var fileCount = 0
        val deadline = if (maxTime == null) Time.now() + COPY_REQUIREMENTS_HARD_TIME_LIMIT else Time.now() + maxTime
        // TODO We need to check if the initial files even exist
        val pathStack = ArrayDeque(realRequest.items.map {
            FileToCopy(
                queries.ucloudToInternal(UCloudFile.create(it.oldPath)).path,
                queries.ucloudToInternal(UCloudFile.create(it.newPath)).path,
                it.conflictPolicy
            )
        })

        while (coroutineContext.isActive && (Time.now() <= deadline)) {
            if (fileCount >= COPY_REQUIREMENTS_HARD_LIMIT) break
            val nextItem = pathStack.removeFirstOrNull() ?: break
            fileCount++


            val nestedFiles = runCatching { NativeFS.listFiles(InternalFile(nextItem.oldPath)) }.getOrNull() ?: continue
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
    override suspend fun execute(actor: Actor, task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesCopyRequest>(task.rawRequest)

        val channel = Channel<FileToCopy>(Channel.UNLIMITED)
        for (reqItem in realRequest.items) {
            channel.send(
                FileToCopy(
                    queries.ucloudToInternal(UCloudFile.create(reqItem.oldPath)).path,
                    queries.ucloudToInternal(UCloudFile.create(reqItem.newPath)).path,
                    reqItem.conflictPolicy,
                )
            )
        }

        val idleCount = AtomicInteger(0)
        val numberOfCoroutines = if (task.requirements.scheduleInBackground) 10 else 1
        coroutineScope {
            (0 until numberOfCoroutines).map {
                launch(backgroundScope.dispatcher) {
                    var didReportIdle = false
                    while (isActive) {
                        val nextItem = channel.poll()
                        if (nextItem == null) {
                            // NOTE(Dan): We don't know if we have run out of files or if there are simply no more work
                            // right now. To deal with this, we keep a count of idle workers. If all workers report that
                            // they are idle, then we must have run out of work.
                            val newIdleCount =
                                if (!didReportIdle) {
                                    didReportIdle = true
                                    idleCount.incrementAndGet()
                                } else {
                                    idleCount.get()
                                }

                            if (newIdleCount == numberOfCoroutines) {
                                break // break before the delay
                            }
                            delay(10)
                            continue
                        }

                        if (didReportIdle) {
                            didReportIdle = false
                            idleCount.decrementAndGet()
                        }
                        val result = try {
                            NativeFS.copy(
                                InternalFile(nextItem.oldPath),
                                InternalFile(nextItem.newPath),
                                nextItem.conflictPolicy
                            )
                        } catch (ex: FSException) {
                            if (log.isDebugEnabled) {
                                log.debug("Caught an exception while copying files: ${ex.printStackTrace()}")
                            }
                            continue
                        }

                        if (result is CopyResult.CreatedDirectory) {
                            val outputFile = result.outputFile
                            val childrenFileNames = try {
                                NativeFS.listFiles(InternalFile(nextItem.oldPath))
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
                }
            }
        }.joinAll()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
