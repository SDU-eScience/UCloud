package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class TrashTask(
    private val trashService: TrashService,
) : TaskHandler {
    override fun TaskContext.canHandle(name: String, request: JsonObject): Boolean {
        return name == Files.trash.fullName && runCatching {
            defaultMapper.decodeFromJsonElement<FilesTrashRequest>(request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesTrashRequest>(request)
        for (reqItem in realRequest.items) {
            // aclService.requirePermission(actor, UCloudFile.create(reqItem.path), FilePermission.WRITE)
        }

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesTrashRequest>(task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        runWork(
            backgroundScope.dispatcher,
            numberOfCoroutines,
            realRequest.items,
            doWork = doWork@{ nextItem ->
                try {
                    val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(nextItem.id))
                    val targetDirectory: InternalFile = TODO("trashService.findTrashDirectory(actor.safeUsername(), internalFile)")
                    val targetFile = InternalFile(targetDirectory.path + "/" + internalFile.fileName())

                    try {
                        nativeFs.stat(targetDirectory)
                    } catch (ex: FSException.NotFound) {
                        nativeFs.createDirectories(targetDirectory)
                    }

                    nativeFs.move(
                        internalFile,
                        targetFile,
                        WriteConflictPolicy.RENAME
                    )
                } catch (ex: FSException) {
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while deleting files: ${ex.stackTraceToString()}")
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
