package dk.sdu.cloud.task.api

data class Task(
    val jobId: String,
    val owner: String,
    val processor: String,
    val title: String?,
    val status: String?,
    val complete: Boolean,
    val startedAt: Long
)

data class TaskUpdate(
    val jobId: String,
    val newTitle: String? = null,
    val speeds: List<Speed> = emptyList(),
    val progress: Progress? = null,
    val complete: Boolean = false,
    val messageToAppend: String? = null,
    val newStatus: String? = null
)

data class Speed(
    var title: String,
    var speed: Double,
    var unit: String
)

data class Progress(
    var title: String,
    var current: Int,
    var maximum: Int
)
