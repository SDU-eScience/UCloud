package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.*
import kotlinx.serialization.Serializable

@Serializable
data class SimpleDuration(val hours: Int, val minutes: Int, val seconds: Int) : DocVisualizable {
    init {
        checkMinimumValue(::seconds, seconds, 0)
        checkMaximumValue(::seconds, seconds, 59)
        checkMinimumValue(::minutes, minutes, 0)
        checkMaximumValue(::minutes, minutes, 59)
    }

    override fun toString() = StringBuilder().apply {
        append(hours.toString().padStart(2, '0'))
        append(':')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }.toString()

    fun toMillis(): Long {
        return (hours * 60L * 60 * 1000 + minutes * 60 * 1000 + seconds * 1000)
    }

    override fun visualize(): DocVisualization {
        return DocVisualization.Inline("${hours}h ${minutes}m ${seconds}s")
    }

    companion object {
        fun fromMillis(durationMs: Long): SimpleDuration {
            val hours = durationMs / (1000 * 60 * 60)
            val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
            val seconds = ((durationMs % (1000 * 60 * 60)) % (1000 * 60)) / 1000

            return SimpleDuration(hours.toInt(), minutes.toInt(), seconds.toInt())
        }
    }
}
