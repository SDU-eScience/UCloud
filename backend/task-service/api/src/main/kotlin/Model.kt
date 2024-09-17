package dk.sdu.cloud.task.api

import kotlinx.serialization.Serializable

const val DEFAULT_TASK_OPERATION = "Background Task"
const val DEFAULT_TASK_PROGRESS = "Accepted"

@Serializable
enum class TaskState{
    IN_QUEUE,
    SUSPENDED,
    RUNNING,
    CANCELLED,
    FAILURE,
    SUCCESS
}

@Serializable
data class BackgroundTask(
    val taskId: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val createdBy: String,
    val status: Status,
    val specification: Specification,
) {

    @Serializable
    data class Status(
        val state: TaskState = TaskState.IN_QUEUE,
        val operation: String = DEFAULT_TASK_OPERATION,
        val progress: String = DEFAULT_TASK_PROGRESS,
    )

    @Serializable
    data class Specification(
        val canPause: Boolean = false,
        val canCancel: Boolean = false
    )
}

@Serializable
data class BackgroundTaskUpdate(
    val taskId: Long,
    val modifiedAt: Long,
    val newStatus: BackgroundTask.Status
)

