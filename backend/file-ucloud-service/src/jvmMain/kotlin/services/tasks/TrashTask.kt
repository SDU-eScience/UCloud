package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

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
            defaultMapper.decodeFromJsonElement<BulkRequest<TrashRequestItem>>(request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<BulkRequest<TrashRequestItem>>(request)

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<BulkRequest<TrashRequestItem>>(task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        runWork(
            backgroundScope.dispatcher,
            numberOfCoroutines,
            realRequest.items,
            doWork = doWork@{ nextItem ->
                try {
                    val file = UCloudFile.create(nextItem.path)
                    val project = pathConverter.fetchProject(file)
                    val internalFile = pathConverter.ucloudToInternal(file)
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
