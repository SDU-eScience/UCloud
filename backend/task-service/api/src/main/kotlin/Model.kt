package dk.sdu.cloud.task.api

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val jobId: String,
    val owner: String,
    val processor: String,
    val title: String? = null,
    val status: String? = null,
    val complete: Boolean,
    val startedAt: Long,
    val modifiedAt: Long
)

@Serializable
data class TaskUpdate(
    val jobId: String,
    val newTitle: String? = null,
    val speeds: List<Speed> = emptyList(),
    val progress: Progress? = null,
    val complete: Boolean = false,
    val messageToAppend: String? = null,
    val newStatus: String? = null
)

@Serializable
open class Speed(
    open val title: String,
    open val speed: Double,
    open val unit: String,
    open val asText: String,
)

@Deprecated("Replaced with Speed", ReplaceWith("Speed"))
typealias SimpleSpeed = Speed

@Serializable
data class Progress(
    var title: String,
    var current: Int,
    var maximum: Int
)
