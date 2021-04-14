package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.Actor
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FilePermission
import dk.sdu.cloud.file.orchestrator.api.Files
import dk.sdu.cloud.file.orchestrator.api.FilesCreateFolderRequest
import dk.sdu.cloud.file.orchestrator.api.FilesDeleteRequest
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.file.ucloud.services.acl.AclService
import dk.sdu.cloud.file.ucloud.services.acl.requirePermission
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class CreateFolderTask : TaskHandler {
    override fun TaskContext.canHandle(actor: Actor, name: String, request: JsonObject): Boolean {
        return name == Files.createFolder.fullName && runCatching {
            defaultMapper.decodeFromJsonElement<FilesCreateFolderRequest>(request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        actor: Actor,
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesCreateFolderRequest>(request)
        for (reqItem in realRequest.items) {
            // TODO There is a fast-path we could optimize where all files are of a single parent and we have write
            //   permissions in that parent
            aclService.requirePermission(actor, UCloudFile.create(reqItem.path), FilePermission.WRITE)
        }

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(actor: Actor, task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesCreateFolderRequest>(task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        runWork(
            backgroundScope.dispatcher,
            numberOfCoroutines,
            realRequest.items,
            doWork = doWork@{ nextItem ->
                val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(nextItem.path))
                try {
                    nativeFs.createDirectories(internalFile)
                } catch (ex: FSException) {
                    if (log.isDebugEnabled) {
                        log.debug("Caught an exception while creating folders: ${ex.stackTraceToString()}")
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
