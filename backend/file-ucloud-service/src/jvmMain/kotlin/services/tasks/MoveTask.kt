package dk.sdu.cloud.file.ucloud.services.tasks

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

// Note: These files are internal
@Serializable
private data class FileToMove(
    override val oldId: String,
    override val newId: String,
    val conflictPolicy: WriteConflictPolicy,
) : WithPathMoving

class MoveTask : TaskHandler {
    override fun TaskContext.canHandle(name: String, request: JsonObject): Boolean {
        return name == Files.move.fullName && runCatching {
            defaultMapper.decodeFromJsonElement<FilesMoveRequest>(request)
        }.isSuccess
    }

    override suspend fun TaskContext.collectRequirements(
        name: String,
        request: JsonObject,
        maxTime: Long?,
    ): TaskRequirements {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesMoveRequest>(request)
        for (reqItem in realRequest.items) {
            val oldId = pathConverter.ucloudToInternal(UCloudFile.create(reqItem.oldId)).path
            val newId = pathConverter.ucloudToInternal(UCloudFile.create(reqItem.newId)).path

            if (newId.startsWith("$oldId/")) {
                throw RPCException("Refusing to move a file to a sub-directory of itself.", HttpStatusCode.BadRequest)
            }
        }

        return if (realRequest.items.size >= 20) TaskRequirements(true, JsonObject(emptyMap()))
        else TaskRequirements(false, JsonObject(emptyMap()))
    }

    override suspend fun TaskContext.execute(task: StorageTask) {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesMoveRequest>(task.rawRequest)

        val numberOfCoroutines = if (realRequest.items.size >= 1000) 10 else 1
        runWork(
            backgroundScope.dispatcher,
            numberOfCoroutines,
            realRequest.items.map {
                FileToMove(
                    pathConverter.ucloudToInternal(UCloudFile.create(it.oldId)).path,
                    pathConverter.ucloudToInternal(UCloudFile.create(it.newId)).path,
                    it.conflictPolicy
                )
            },
            doWork = doWork@{ nextItem ->
                try {
                    val needsToRecurse = nativeFs.move(
                        InternalFile(nextItem.oldId),
                        InternalFile(nextItem.newId),
                        nextItem.conflictPolicy
                    ).needsToRecurse

                    if (needsToRecurse) {
                        val childrenFileNames = runCatching { nativeFs.listFiles(InternalFile(nextItem.oldId)) }
                            .getOrDefault(emptyList())

                        for (childFileName in childrenFileNames) {
                            channel.send(
                                FileToMove(
                                    nextItem.oldId + "/" + childFileName,
                                    nextItem.newId + "/" + childFileName,
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
