package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.Actor
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.file.ucloud.services.acl.AclService
import dk.sdu.cloud.file.ucloud.services.acl.requirePermission
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

// Note: These files are internal
@Serializable
private data class FileToMove(
    override val oldPath: String,
    override val newPath: String,
    val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving

class MoveTask : TaskHandler {
    override fun TaskContext.canHandle(actor: Actor, name: String, request: JsonObject): Boolean {
        return name == Files.move.fullName && runCatching {
            defaultMapper.decodeFromJsonElement<FilesMoveRequest>(request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        actor: Actor,
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesMoveRequest>(request)
        for (reqItem in realRequest.items) {
            aclService.requirePermission(actor, UCloudFile.create(reqItem.oldPath), FilePermission.WRITE)
            aclService.requirePermission(actor, UCloudFile.create(reqItem.newPath), FilePermission.WRITE)
        }

        // TODO It might be beneficial to go into the background if the policy is MERGE and the destination exists

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(actor: Actor, task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesMoveRequest>(task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        runWork(
            backgroundScope.dispatcher,
            numberOfCoroutines,
            realRequest.items.map {
                FileToMove(
                    pathConverter.ucloudToInternal(UCloudFile.create(it.oldPath)).path,
                    pathConverter.ucloudToInternal(UCloudFile.create(it.newPath)).path,
                    it.conflictPolicy
                )
            },
            doWork = doWork@{ nextItem ->
                try {
                    val needsToRecurse = nativeFs.move(
                        InternalFile(nextItem.oldPath),
                        InternalFile(nextItem.newPath),
                        nextItem.conflictPolicy
                    ).needsToRecurse

                    if (needsToRecurse) {
                        val childrenFileNames = runCatching { nativeFs.listFiles(InternalFile(nextItem.oldPath)) }
                            .getOrDefault(emptyList())

                        for (childFileName in childrenFileNames) {
                            channel.send(
                                FileToMove(
                                    nextItem.oldPath + "/" + childFileName,
                                    nextItem.newPath + "/" + childFileName,
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

    companion object : Loggable {
        override val log = logger()
    }
}
