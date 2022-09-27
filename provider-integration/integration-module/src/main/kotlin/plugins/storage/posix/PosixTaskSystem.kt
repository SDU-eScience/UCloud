package dk.sdu.cloud.plugins.storage.posix

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.controllers.TaskIpc
import dk.sdu.cloud.controllers.TaskSpecification
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.Files as NioFiles

/**
 * The Posix Task system is responsible for managing a set of file-system related tasks. A task is started when a
 * command comes from the user. At this point, the task system evaluates the task and decides to either complete it
 * immediately or push it to the background. If a task is pushed to the background, it will automatically be restarted
 * in the case of a restart or failure. Tasks which are completed immediately will not do this.
 *
 * The task system depends on the accompanying posix collection plugin. At the root of each drive, this plugin will
 * store a hidden folder (even if `filterHiddenFiles=false`). This folder contains each file per task which is a JSON
 * serialized version of the task.
 */
class PosixTaskSystem(
    private val pluginContext: PluginContext,
    private val pathConverter: PathConverter,
) {
    suspend fun registerTask(task: PosixTask) {
        // NOTE(Dan): We first find the task folder to make sure that the arguments are valid
        val taskFolder = findAndInitTaskFolder(task.collectionId)

        val response = pluginContext.ipcClient.sendRequest(TaskIpc.register, TaskSpecification(task.title))
        task.id = response.id
        task.timestamp = Time.now()
        task.collectionId = task.collectionId

        val file = NativeFile.open(
            taskFolder.path + "/" + TASK_PREFIX + task.id + TASK_SUFFIX,
            readOnly = false,
            truncateIfNeeded = true
        )

        file.writeText(
            defaultMapper.encodeToString(PosixTask.serializer(), task),
            autoClose = true
        )
    }

    suspend fun markTaskAsComplete(collectionId: String, taskId: String) {
        val file = InternalFile(findAndInitTaskFolder(collectionId).path + "/" + TASK_PREFIX + taskId + TASK_SUFFIX).toNioPath()
        pluginContext.ipcClient.sendRequest(TaskIpc.markAsComplete, FindByStringId(taskId))
        NioFiles.delete(file)
    }

    suspend fun retrieveCurrentTasks(collectionId: String): List<PosixTask> {
        return listFiles(findAndInitTaskFolder(collectionId).path).asSequence()
            .filter {
                val name = it.fileName()
                name.startsWith(TASK_PREFIX) && name.endsWith(TASK_SUFFIX)
            }
            .mapNotNull {
                runCatching {
                    NativeFile.open(it, readOnly = true).readText(autoClose = true)
                }.getOrNull()
            }
            .mapNotNull {
                runCatching {
                    defaultMapper.decodeFromString(PosixTask.serializer(), it)
                }.getOrNull()
            }
            .toList()
    }

    private suspend fun findAndInitTaskFolder(collectionId: String): InternalFile {
        val internalFile = pathConverter.ucloudToInternal(UCloudFile.create("/$collectionId/${TASK_FOLDER}"))
        val folder = internalFile.toNioPath()
        if (!NioFiles.exists(folder)) {
            NioFiles.createDirectories(
                folder,
                PosixFilePermissions.asFileAttribute(posixFilePermissionsFromInt("770".toInt(8)))
            )
        }
        return internalFile
    }

    companion object {
        const val TASK_FOLDER = ".ucloud-tasks"
        const val TASK_PREFIX = "task_"
        const val TASK_SUFFIX = ".json"
    }
}

@Serializable
sealed class PosixTask {
    abstract val title: String
    abstract var collectionId: String
    abstract var id: String
    abstract var timestamp: Long

    @Serializable
    @SerialName("move_to_trash")
    data class MoveToTrash(
        override val title: String,
        override var collectionId: String,
        val resolvedCollection: FileCollection,
        val request: List<FilesProviderTrashRequestItem>,
        override var id: String = "",
        override var timestamp: Long = Time.now(),
    ) : PosixTask()

    @Serializable
    @SerialName("empty_trash")
    data class EmptyTrash(
        override val title: String,
        override var collectionId: String,
        val request: FilesProviderEmptyTrashRequestItem,
        override var id: String = "",
        override var timestamp: Long = Time.now(),
    ) : PosixTask()

    @Serializable
    @SerialName("move")
    data class Move(
        override val title: String,
        override var collectionId: String,
        val request: FilesProviderMoveRequestItem,
        override var id: String = "",
        override var timestamp: Long = Time.now(),
    ) : PosixTask()

    @Serializable
    @SerialName("copy")
    data class Copy(
        override val title: String,
        override var collectionId: String,
        val request: FilesProviderCopyRequestItem,
        override var id: String = "",
        override var timestamp: Long = Time.now(),
    ) : PosixTask()
}

