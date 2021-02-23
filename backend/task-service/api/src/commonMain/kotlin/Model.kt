package dk.sdu.cloud.task.api

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val jobId: String,
    val owner: String,
    val processor: String,
    val title: String?,
    val status: String?,
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

@JsonDeserialize(`as` = SimpleSpeed::class)
interface Speed {
    val title: String
    val speed: Double
    val unit: String
    val asText: String
        get() = "${String.format("%.3f", speed)} $unit"
}

@Serializable
data class SimpleSpeed(
    override val title: String,
    override var speed: Double,
    override var unit: String,
    override val asText: String = "${String.format("%.3f", speed)} $unit"
) : Speed

class MeasuredSpeedInteger(
    override val title: String,
    override val unit: String,
    private val customFormatter: ((speed: Double) -> String)? = null
) : Speed {
    private var lastUpdate: Long = Time.now()
    private var counter: Long = 0
    private var isUpdating = false
    private var isFirstUpdate = true
    private val mutex = Mutex()

    override var speed: Double = 0.0
        private set
        get() {
            update()
            return field
        }

    override val asText: String
        get() = customFormatter?.invoke(speed) ?: super.asText

    suspend fun start() {
        mutex.withLock {
            lastUpdate = Time.now()
            counter = 0
            isFirstUpdate = true
        }
    }

    fun increment(count: Long) {
        counter += count
    }

    private suspend fun update() {
        if (isUpdating) return
        val now = Time.now()
        if (now != -1L && now - lastUpdate < 1_000 && !isFirstUpdate) return

        mutex.withLock {
            if (!isUpdating) {
                isUpdating = true
                val delta = Time.now() - lastUpdate
                val unitsPerSecond = (counter / (delta / 1000.0))
                counter = 0
                speed = unitsPerSecond
                lastUpdate = Time.now()
                isFirstUpdate = false
                isUpdating = false
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

@Serializable
data class Progress(
    var title: String,
    var current: Int,
    var maximum: Int
)
