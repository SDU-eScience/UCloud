package dk.sdu.cloud.task.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dk.sdu.cloud.service.Loggable

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

@JsonDeserialize(`as` = SimpleSpeed::class)
interface Speed {
    val title: String
    val speed: Double
    val unit: String
}

data class SimpleSpeed(
    override val title: String,
    override var speed: Double,
    override var unit: String
) : Speed

class MeasuredSpeedInteger(
    override val title: String,
    private val unitFormatter: (speed: Double) -> String
) : Speed {
    private var lastUpdate: Long = System.currentTimeMillis()
    private var counter: Long = 0
    private var isUpdating = false
    private var isFirstUpdate = true

    override var speed: Double = 0.0
        private set
        get() {
            update()
            return field
        }

    override var unit: String = unitFormatter(0.0)
        private set
        get() {
            update()
            return field
        }

    fun start() {
        synchronized(this) {
            lastUpdate = System.currentTimeMillis()
            counter = 0
            isFirstUpdate = true
        }
    }

    fun increment(count: Long) {
        counter += count
    }

    private fun update() {
        if (isUpdating) return
        val now = System.currentTimeMillis()
        if (now != -1L && now - lastUpdate < 1_000 && !isFirstUpdate) return

        synchronized(this) {
            if (!isUpdating) {
                isUpdating = true
                val delta = System.currentTimeMillis() - lastUpdate
                val unitsPerSecond = (counter / (delta / 1000.0))
                counter = 0
                unit = unitFormatter(unitsPerSecond)
                speed = unitsPerSecond
                lastUpdate = System.currentTimeMillis()
                isFirstUpdate = false
                isUpdating = false
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

data class Progress(
    var title: String,
    var current: Int,
    var maximum: Int
)
