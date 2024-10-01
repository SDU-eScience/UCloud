package dk.sdu.cloud.task.api

import dk.sdu.cloud.service.Time
import kotlinx.serialization.Serializable

const val DEFAULT_TASK_TITLE = "Background task"
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
    val provider: String,
    val status: Status,
    val specification: Specification,
    val icon: String?
) {

    @Serializable
    data class Status(
        val state: TaskState? = TaskState.IN_QUEUE,
        val title: String? = DEFAULT_TASK_TITLE,
        val body: String?,
        val progress: String?,
        val progressPercentage: Double? = -1.0,
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
    val modifiedAt: Long = Time.now(),
    val newStatus: BackgroundTask.Status
)

