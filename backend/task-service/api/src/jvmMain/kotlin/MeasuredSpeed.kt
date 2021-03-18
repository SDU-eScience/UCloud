package dk.sdu.cloud.task.api

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.task.api.Speed

class MeasuredSpeedInteger(
    title: String,
    unit: String,
    private val customFormatter: ((speed: Double) -> String)? = null
) : Speed(title, 0.0, unit, "0.0") {
    private var lastUpdate: Long = Time.now()
    private var counter: Long = 0
    private var isUpdating = false
    private var isFirstUpdate = true

    override var speed: Double = 0.0
        private set
        get() {
            update()
            return field
        }

    override val asText: String
        get() = customFormatter?.invoke(speed) ?: "${String.format("%.3f", speed)} $unit"

    fun start() {
        synchronized(this) {
            lastUpdate = Time.now()
            counter = 0
            isFirstUpdate = true
        }
    }

    fun increment(count: Long) {
        counter += count
    }

    private fun update() {
        if (isUpdating) return
        val now = Time.now()
        if (now != -1L && now - lastUpdate < 1_000 && !isFirstUpdate) return

        synchronized(this) {
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
