package dk.sdu.cloud.plugins.storage.ucloud.tasks

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.Prometheus
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.child
import dk.sdu.cloud.plugins.storage.ucloud.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.toReadableStacktrace
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    init {
        println("Staging folder is: $stagingFolder")
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
                try {
                    if (stagingFolder == null) {
                        nativeFs.delete(internalFile)
                        nativeFs.createDirectories(internalFile)
                    } else {
                        println("Deleting via staging folder")
                        val filesToDelete = nativeFs.listFiles(internalFile)
                        filesToDelete.forEach {
                            try {
                                val src = internalFile.child(it)
                                val dst = stagingFolder.child(UUID.randomUUID().toString())
                                println("$src -> $dst")
                                java.nio.file.Files.move(
                                    Path(src.path),
                                    Path(dst.path),
                                    StandardCopyOption.REPLACE_EXISTING
                                )
                                fs.move(src, dst, WriteConflictPolicy.RENAME)
                            } catch (ignored: FSException.NotFound) {}
                        }
                    }

                    println("Requesting scan of ${driveInfo.drive.ucloudId}")
                    usageScan2.requestScan(driveInfo.drive.ucloudId)
                } catch (ex: FSException) {
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while deleting files during emptying of trash: ${ex.stackTraceToString()}")
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
