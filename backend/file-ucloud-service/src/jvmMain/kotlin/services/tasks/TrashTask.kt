package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.Actor
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.file.ucloud.services.acl.AclService
import dk.sdu.cloud.file.ucloud.services.acl.requirePermission
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class TrashTask(
    private val trashService: TrashService,
) : TaskHandler {
    override fun TaskContext.canHandle(actor: Actor, name: String, request: JsonObject): Boolean {
        return name == Files.move.fullName && runCatching {
            defaultMapper.decodeFromJsonElement<FilesTrashRequest>(request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        actor: Actor,
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesTrashRequest>(request)
        for (reqItem in realRequest.items) {
            aclService.requirePermission(actor, UCloudFile.create(reqItem.path), FilePermission.WRITE)
        }

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(actor: Actor, task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesTrashRequest>(task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        runWork(
            backgroundScope.dispatcher,
            numberOfCoroutines,
            realRequest.items,
            doWork = doWork@{ nextItem ->
                try {
                    val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(nextItem.path))
                    val targetDirectory = trashService.findTrashDirectory(actor.safeUsername(), internalFile)
                    val targetFile = InternalFile(targetDirectory.path + "/" + internalFile.fileName())
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
