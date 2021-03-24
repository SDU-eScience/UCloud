package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.services.acl.AclService
import dk.sdu.cloud.file.ucloud.services.acl.requirePermission
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.isActive
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
) : WithPathMoving

// Note: These files are internal
@Serializable
private data class CopyTaskRequirements(
    val filesToCopy: List<FileToCopy>,
    val hardLimitReached: Boolean,
)

const val COPY_REQUIREMENTS_HARD_LIMIT = 10_000

class CopyTask(
    private val aclService: AclService,
    private val queries: FileQueries,
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
    ): JsonObject {
        val realRequest = defaultMapper.decodeFromJsonElement<FilesCopyRequest>(request)

        // Check permissions
        for (reqItem in realRequest.items) {
            aclService.requirePermission(reqItem.oldPath, actor, FilePermission.READ)
            aclService.requirePermission(reqItem.newPath, actor, FilePermission.WRITE)
        }

        val filesToCopy = ArrayList<FileToCopy>()
        val deadline = if (maxTime == null) null else Time.now() + maxTime
        // TODO We need to check if the initial files even exist
        val pathStack = ArrayDeque(realRequest.items.map {
            FileToCopy(
                queries.convertUCloudPathToInternalFile(UCloudFile(it.oldPath.normalize())).path,
                queries.convertUCloudPathToInternalFile(UCloudFile(it.newPath.normalize())).path,
            )
        })

        while (coroutineContext.isActive && (deadline == null || Time.now() <= deadline)) {
            if (filesToCopy.size >= COPY_REQUIREMENTS_HARD_LIMIT) break
            val nextItem = pathStack.removeFirstOrNull() ?: break
            filesToCopy.add(nextItem)

            val nestedFiles = queries.listInternalFilesOrNull(Actor.System, InternalFile(nextItem.oldPath)) ?: continue
            nestedFiles.forEach {
                val oldPath = it.path
                val newPath = nextItem.newPath + "/" + it.path.removePrefix(nextItem.oldPath + "/")

                pathStack.add(FileToCopy(oldPath, newPath))
            }
        }

        val hardLimitReached = filesToCopy.size >= COPY_REQUIREMENTS_HARD_LIMIT
        return defaultMapper.encodeToJsonElement(
            CopyTaskRequirements(if (hardLimitReached) emptyList() else filesToCopy, hardLimitReached)
        ) as JsonObject
    }

    override suspend fun execute(actor: Actor, task: StorageTask) {
        val requirements = defaultMapper.decodeFromJsonElement<CopyTaskRequirements>(task.requirements)
        if (requirements.hardLimitReached) {
            // We will try to find the files and copy them ourselves
        }
    }
}
