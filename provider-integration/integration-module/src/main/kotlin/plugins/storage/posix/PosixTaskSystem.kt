package dk.sdu.cloud.plugins.storage.posix

import dk.sdu.cloud.controllers.TaskIpc
import dk.sdu.cloud.controllers.TaskSpecification
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.ipcClient
import dk.sdu.cloud.plugins.storage.InternalFile
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
        TODO()
        /*
        val file = InternalFile(findAndInitTaskFolder(collectionId).path + "/" + TASK_PREFIX + taskId + TASK_SUFFIX)
        pluginContext.ipcClient.sendRequest(TaskIpc.markAsComplete, FindByStringId(taskId))
        unlink(file.path)
         */
    }

    suspend fun retrieveCurrentTasks(collectionId: String): List<PosixTask> {
        TODO()
        /*
        return listFiles(findAndInitTaskFolder(collectionId)).asSequence()
            .filter {
                val name = it.path.fileName()
                name.startsWith(TASK_PREFIX) && name.endsWith(TASK_SUFFIX)
            }
            .mapNotNull {
                runCatching {
                    NativeFile.open(it.path, readOnly = true).readText(autoClose = true)
                }.getOrNull()
            }
            .mapNotNull {
                runCatching {
                    defaultMapper.decodeFromString<PosixTask>(it)
                }.getOrNull()
            }
            .toList()
         */
    }

    private suspend fun findAndInitTaskFolder(collectionId: String): InternalFile {
        TODO()
        /*
        val folder = pathConverter.ucloudToInternal(UCloudFile.create("/$collectionId/${TASK_FOLDER}"))
        if (!fileExists(folder.path)) {
            if (mkdir(folder.path, "770".toUInt(8)) != 0) {
                throw IllegalStateException("Unable to create task folder: ${folder.path}")
            }
        }
        return folder
         */
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
        val request: FilesProviderTrashRequestItem,
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

